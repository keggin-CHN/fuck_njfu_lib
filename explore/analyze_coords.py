"""Analyze seat coordinate patterns to detect table groupings"""
import json

with open(r"c:\code\fuck_njfu_lib\explore\seat_coordinates.json", "r", encoding="utf-8") as f:
    seats = json.load(f)

print(f"Total seats: {len(seats)}")

# Parse coords
parsed = []
for s in seats:
    coord = s.get("coordinate", "")
    if coord and "," in coord:
        x, y = map(float, coord.split(","))
        parsed.append({"name": s["devName"], "x": x, "y": y, "coord": coord})

print(f"Seats with coordinates: {len(parsed)}")

# Coordinate ranges
xs = [p["x"] for p in parsed]
ys = [p["y"] for p in parsed]
print(f"X range: {min(xs):.2f} - {max(xs):.2f}")
print(f"Y range: {min(ys):.2f} - {max(ys):.2f}")

# Find unique X and Y values
from collections import Counter

def round_to(val, precision=0.5):
    return round(val / precision) * precision

x_clusters = Counter(round_to(x, 0.5) for x in xs)
y_clusters = Counter(round_to(y, 0.5) for y in ys)

print(f"\nUnique X clusters (columns): {len(x_clusters)}")
for x, cnt in sorted(x_clusters.items()):
    print(f"  X~{x:.1f}: {cnt} seats")

print(f"\nUnique Y clusters (rows): {len(y_clusters)}")
for y, cnt in sorted(y_clusters.items()):
    print(f"  Y~{y:.1f}: {cnt} seats")

# Show first 30 seats
parsed.sort(key=lambda s: s["name"])
print("\nFirst 30 seats:")
for p in parsed[:30]:
    print(f"  {p['name']}: ({p['x']:.3f}, {p['y']:.3f})")

# Y-gap analysis for consecutive pairs
print("\nY-gap for first 20 pairs:")
for i in range(0, min(40, len(parsed)), 2):
    if i+1 < len(parsed):
        s1, s2 = parsed[i], parsed[i+1]
        y_gap = abs(s1["y"] - s2["y"])
        x_gap = abs(s1["x"] - s2["x"])
        print(f"  {s1['name']}({s1['x']:.2f},{s1['y']:.2f}) <-> {s2['name']}({s2['x']:.2f},{s2['y']:.2f})  Ygap={y_gap:.2f} Xgap={x_gap:.2f}")
