import socket
import time
import json

HOST = "127.0.0.1"
PORT = 9999


class MineBridgeClient:
    def __init__(self):
        self.client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.client.connect((HOST, PORT))

    def send_message(self, message):
        self.client.sendall((message + "\n").encode())
        reply = self.client.recv(65536).decode().strip()

        try:
            return json.loads(reply)
        except json.JSONDecodeError:
            return reply

    def ping(self):
        start = time.time()
        response = self.send_message("ping")
        end = time.time()

        latency_ms = (end - start) * 1000

        return {
            "response": response,
            "latency": latency_ms
        }

    def get_player_position(self):
        return self.send_message("get_player_position")

    def set_player_position(self, x, y, z):
        return self.send_message(f"set_player_position {x} {y} {z}")
    
    def get_entities(self):
        return self.send_message("get_entities")

    def get_players(self):
        return self.send_message("get_players")

    def get_local_player(self):
        return self.send_message("get_local_player")

    def close(self):
        self.client.close()


def test():
    client = MineBridgeClient()

    pingResult = client.ping()
    print("Ping:", pingResult['response'], pingResult['latency'])

    pos = client.get_player_position()
    print("Position:", pos)
    client.set_player_position(-100,75.0,-270)
    print(client.send_message("get_block -100 70 -270"))
    print(client.send_message("find_block 10 diamond_ore iron_ore"))

    print(client.get_local_player())
    print(client.get_players())
    print(client.get_entities())

    client.close()

if __name__ == "__main__":
    test()