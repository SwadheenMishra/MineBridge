package com.minebridge.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class MineBridgeClient implements ClientModInitializer {
	private static final int PORT = 9999;

	@Override
	public void onInitializeClient() {
		startServer();
	}

	private void startServer() {
		Thread serverThread = new Thread(() -> {
			try (ServerSocket serverSocket = new ServerSocket(
					PORT,
					50,
					InetAddress.getByName("127.0.0.1")
			)) {
				System.out.println("[MineBridge] Server started on localhost:" + PORT);

				while (true) {
					Socket clientSocket = serverSocket.accept();
					System.out.println("[MineBridge] Python client connected.");
					handleClient(clientSocket);
				}

			} catch (IOException e) {
				System.err.println("[MineBridge] Server error: " + e.getMessage());
			}
		});

		serverThread.setName("MineBridge-Server");
		serverThread.setDaemon(true);
		serverThread.start();
	}

	private void handleClient(Socket clientSocket) {
		try (
				Socket socket = clientSocket;
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream())
				);
				PrintWriter writer = new PrintWriter(
						socket.getOutputStream(),
						true
				)
		) {
			String message;

			while ((message = reader.readLine()) != null) {
				System.out.println("[MineBridge] Received: " + message);

				String response = handleCommand(message);
				writer.println(response);
			}

			System.out.println("[MineBridge] Python client disconnected.");

		} catch (IOException e) {
			System.err.println("[MineBridge] Client error: " + e.getMessage());
		}
	}

	private String handleCommand(String message) {
		String[] arr = message.trim().split(" ");

		if (arr.length == 0 || arr[0].isEmpty()) {
			return jsonError("empty command");
		}

		String command = arr[0].toLowerCase();

		if (command.equals("ping")) {
			return "pong";
		}

		else if (command.equals("get_player_position")) {
			return getPlayerPosition();
		}

		else if (command.equals("set_player_position")) {
			if (arr.length != 4) {
				return jsonError("usage: set_player_position <x> <y> <z>");
			}

			try {
				double x = Double.parseDouble(arr[1]);
				double y = Double.parseDouble(arr[2]);
				double z = Double.parseDouble(arr[3]);

				return setPlayerPosition(x, y, z);

			} catch (NumberFormatException e) {
				return jsonError("x, y, and z must be numbers");
			}
		}

		else if (command.equals("get_block")) {
			if (arr.length != 4) {
				return "usage: get_block <x> <y> <z>";
			}

			try {
				int x = (int) Double.parseDouble(arr[1]);
				int y = (int) Double.parseDouble(arr[2]);
				int z = (int) Double.parseDouble(arr[3]);

				return getBlock(x, y, z);

			} catch (NumberFormatException e) {
				return "x, y, and z must be numbers";
			}
		}

		else if (command.equals("find_block")) {
			if (arr.length < 3) {
				return jsonError("usage: find_block <distance> <name1> <name2> ...");
			}

			try {
				int distance = Integer.parseInt(arr[1]);
				String[] names = Arrays.copyOfRange(arr, 2, arr.length);

				return findBlock(distance, names);

			} catch (NumberFormatException e) {
				return jsonError("distance must be an integer");
			}
		}

		else if (command.equals("world_to_screen")) {
			if ((arr.length - 1) % 3 != 0) {
				return jsonError("usage: world_to_screen <x1> <y1> <z1> <x2> <y2> <z2> ...");
			}

			try {
				double[] coords = new double[arr.length - 1];

				for (int i = 1; i < arr.length; i++) {
					coords[i - 1] = Double.parseDouble(arr[i]);
				}

				return worldToScreen(coords);

			} catch (NumberFormatException e) {
				return jsonError("all coordinates must be numbers");
			}
		}

		else {
			return jsonError("unknown command");
		}
	}

	private String getPlayerPosition() {
		Minecraft client = Minecraft.getInstance();
		CompletableFuture<String> future = new CompletableFuture<>();

		client.execute(() -> {
			if (client.player == null) {
				future.complete(jsonError("player is null"));
				return;
			}

			double x = client.player.getX();
			double y = client.player.getY();
			double z = client.player.getZ();

			future.complete(
					"{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}"
			);
		});

		return getFutureResult(future);
	}

	private String setPlayerPosition(double x, double y, double z) {
		Minecraft client = Minecraft.getInstance();
		CompletableFuture<String> future = new CompletableFuture<>();

		client.execute(() -> {
			if (client.player == null) {
				future.complete(jsonError("player is null"));
				return;
			}

			client.player.setPos(x, y, z);

			future.complete(
					"{\"success\":true,\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}"
			);
		});

		return getFutureResult(future);
	}

	private String getBlock(int x, int y, int z) {
		Minecraft client = Minecraft.getInstance();
		CompletableFuture<String> future = new CompletableFuture<>();

		client.execute(() -> {
			if (client.level == null) {
				future.complete("level is null");
				return;
			}

			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = client.level.getBlockState(pos);

			future.complete(getBlockName(state));
		});

		return getFutureResult(future);
	}

	private String findBlock(int distance, String[] names) {
		Minecraft client = Minecraft.getInstance();
		CompletableFuture<String> future = new CompletableFuture<>();

		client.execute(() -> {
			if (client.player == null || client.level == null) {
				future.complete(jsonError("player or level is null"));
				return;
			}

			BlockPos playerPos = client.player.blockPosition();

			StringBuilder json = new StringBuilder();
			json.append("{");

			for (int i = 0; i < names.length; i++) {
				String targetName = names[i].toLowerCase();

				if (i > 0) {
					json.append(",");
				}

				json.append("\"").append(escapeJson(targetName)).append("\":[");
				boolean firstResult = true;

				for (int x = -distance; x <= distance; x++) {
					for (int y = -distance; y <= distance; y++) {
						for (int z = -distance; z <= distance; z++) {
							BlockPos pos = playerPos.offset(x, y, z);
							BlockState state = client.level.getBlockState(pos);

							String blockName = getBlockName(state);

							if (!matchesBlockName(blockName, targetName)) {
								continue;
							}

							if (!firstResult) {
								json.append(",");
							}

							firstResult = false;

							json.append("{");
							json.append("\"world_position\":{");
							json.append("\"x\":").append(pos.getX()).append(",");
							json.append("\"y\":").append(pos.getY()).append(",");
							json.append("\"z\":").append(pos.getZ());
							json.append("}");
							json.append("}");
						}
					}
				}

				json.append("]");
			}

			json.append("}");
			future.complete(json.toString());
		});

		return getFutureResult(future);
	}


	private String worldToScreen(double[] coords) {
		Minecraft client = Minecraft.getInstance();
		CompletableFuture<String> future = new CompletableFuture<>();

		client.execute(() -> {
			if (client.player == null || client.level == null) {
				future.complete(jsonError("player or level is null"));
				return;
			}

			int width = client.getWindow().getWidth();
			int height = client.getWindow().getHeight();

			StringBuilder json = new StringBuilder();
			json.append("[");

			for (int i = 0; i < coords.length; i += 3) {
				double x = coords[i];
				double y = coords[i + 1];
				double z = coords[i + 2];

				if (i > 0) {
					json.append(",");
				}

				try {
					Vec3 worldPos = new Vec3(x, y, z);

					Vector3f p2s = client.gameRenderer
							.projectPointToScreen(worldPos)
							.toVector3f();

					if (p2s.z() >= 1.0f) {
						json.append("null");
						continue;
					}

					double screenX = (p2s.x() + 1.0) * (0.5 * width);
					double screenY = (1.0 - p2s.y()) * (0.5 * height);

					json.append("{");
					json.append("\"x\":").append((int) screenX).append(",");
					json.append("\"y\":").append((int) screenY);
					json.append("}");

				} catch (Exception e) {
					json.append("null");
				}
			}

			json.append("]");
			future.complete(json.toString());
		});

		return getFutureResult(future);
	}

	private String getBlockName(BlockState state) {
		Block block = state.getBlock();
		var id = BuiltInRegistries.BLOCK.getKey(block);
		return id.toString();
	}

	private boolean matchesBlockName(String fullBlockName, String targetName) {
		fullBlockName = fullBlockName.toLowerCase();
		targetName = targetName.toLowerCase();

		if (fullBlockName.equals(targetName)) {
			return true;
		}

		if (fullBlockName.startsWith("minecraft:")) {
			return fullBlockName.substring("minecraft:".length()).equals(targetName);
		}

		return false;
	}

	private String getFutureResult(CompletableFuture<String> future) {
		try {
			return future.get();
		} catch (Exception e) {
			return jsonError(e.getMessage());
		}
	}

	private String jsonError(String message) {
		return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
	}

	private String escapeJson(String text) {
		if (text == null) return "";

		return text
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}
}