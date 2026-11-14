from pathlib import Path
import math

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
MOD = ROOT.parent
ASSETS = MOD / "src/main/resources/assets/destroy_extras"
OBJ = ASSETS / "models/block/multiblock/membrane_electrolyzer.obj"
ATLAS = ASSETS / "textures/block/multiblock/membrane_electrolyzer.png"
ORIGINAL = ROOT / "membrane_electrolyzer-original.obj"
ORIGINAL_ATLAS = ROOT / "membrane_electrolyzer-original.png"


def parse_obj(path):
    vertices, uvs, normals, faces = [], [], [], []
    for line in path.read_text().splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "v":
            vertices.append(tuple(map(float, parts[1:4])))
        elif parts[0] == "vt":
            uvs.append(tuple(map(float, parts[1:3])))
        elif parts[0] == "vn":
            normals.append(tuple(map(float, parts[1:4])))
        elif parts[0] == "f":
            faces.append([tuple(int(value) - 1 for value in item.split("/")) for item in parts[1:]])
    return vertices, uvs, normals, faces


def normal_of(points):
    a, b, c = (np.array(points[i]) for i in range(3))
    normal = np.cross(b - a, c - a)
    return tuple(normal / np.linalg.norm(normal))


def tile_uv(col, row):
    # OBJ использует flip_v=true: нулевая V — нижняя строка изображения.
    return (col / 16, 1 - (row + 1) / 16), ((col + 1) / 16, 1 - row / 16)


def build_tiles():
    atlas = Image.open(ORIGINAL_ATLAS).convert("RGBA")
    draw = ImageDraw.Draw(atlas)
    roles = [
        (0, "cathode", (91, 220, 238), "out"),
        (1, "anode", (244, 196, 48), "out"),
        (2, "catholyte", (183, 104, 220), "out"),
        (3, "input", (64, 139, 230), "in"),
    ]
    for col, _, color, direction in roles:
        x, y = col * 16, 16
        draw.rectangle((x, y, x + 15, y + 15), fill=(25, 31, 35, 255))
        draw.rectangle((x + 1, y + 1, x + 14, y + 14), outline=color, width=2)
        if direction == "out":
            arrow = [(7, 12), (9, 12), (9, 8), (12, 8), (8, 3), (4, 8), (7, 8)]
        else:
            arrow = [(7, 3), (9, 3), (9, 7), (12, 7), (8, 12), (4, 7), (7, 7)]
        draw.polygon([(x + px, y + py) for px, py in arrow], fill=color)
    for col, sign, color in ((4, "+", (190, 55, 48)), (5, "−", (67, 113, 190))):
        x, y = col * 16, 16
        draw.rectangle((x, y, x + 15, y + 15), fill=(32, 35, 37, 255))
        draw.rectangle((x + 1, y + 1, x + 14, y + 14), outline=color, width=2)
        draw.rectangle((x + 4, y + 7, x + 11, y + 8), fill=(240, 240, 230, 255))
        if sign == "+":
            draw.rectangle((x + 7, y + 4, x + 8, y + 11), fill=(240, 240, 230, 255))
    atlas.save(ATLAS)


def add_face(vertices, uvs, normals, faces, points, tile):
    (u0, v0), (u1, v1) = tile_uv(*tile)
    face_uvs = ((u0, v0), (u1, v0), (u1, v1), (u0, v1))
    normal = normal_of(points)
    face = []
    for point, uv in zip(points, face_uvs):
        face.append((len(vertices), len(uvs), len(normals)))
        vertices.append(point)
        uvs.append(uv)
    normals.append(normal)
    faces.append(face)


def update_geometry():
    vertices, uvs, normals, faces = parse_obj(ORIGINAL if ORIGINAL.exists() else OBJ)
    if not ORIGINAL.exists():
        ORIGINAL.write_text(OBJ.read_text())
        ORIGINAL_ATLAS.write_bytes(ATLAS.read_bytes())

    # Убираем большие восьмиугольные торцы газовых коллекторов: они похожи на ложные порты.
    kept = []
    for face in faces:
        points = [vertices[item[0]] for item in face]
        is_gas_cap = False
        for center_x in (-0.5, 1.5):
            side_cap = all(abs(abs(point[2]) - 0.9) < 1e-6 for point in points) and all(
                abs(point[0] - center_x) <= 0.325 and abs(point[1] - 1.55) <= 0.325 for point in points)
            top_cap = all(abs(point[1] - 2.0) < 1e-6 for point in points) and all(
                abs(point[0] - center_x) <= 0.26 and abs(point[2] - 0.5) <= 0.26 for point in points)
            is_gas_cap |= side_cap or top_cap
        is_work_port = all(abs(point[0] - 1.0) < 1e-6 for point in points) and all(
            abs(point[1] + 0.5) <= 0.26 and abs(point[2] + 0.5) <= 0.26 for point in points)
        is_work_port |= all(abs(point[0] + 1.0) < 1e-6 for point in points) and all(
            abs(point[1] + 0.5) <= 0.26 and abs(point[2] - 0.5) <= 0.26 for point in points)
        if not is_gas_cap and not is_work_port:
            kept.append(face)
    faces = kept

    steel = (0, 0)
    for center_x in (-0.5, 1.5):
        for z in (-0.901, 0.901):
            points = [
                (center_x - 0.19, 1.36, z),
                (center_x + 0.19, 1.36, z),
                (center_x + 0.19, 1.74, z),
                (center_x - 0.19, 1.74, z),
            ]
            if z < 0:
                points.reverse()
            add_face(vertices, uvs, normals, faces, points, steel)

    # Плоские таблички тонкие, но после общего шага ниже имеют обратную сторону.
    add_face(vertices, uvs, normals, faces,
             [(-0.81, 2.0, 0.19), (-0.19, 2.0, 0.19), (-0.19, 2.0, 0.81), (-0.81, 2.0, 0.81)], (0, 1))
    add_face(vertices, uvs, normals, faces,
             [(1.19, 2.0, 0.19), (1.81, 2.0, 0.19), (1.81, 2.0, 0.81), (1.19, 2.0, 0.81)], (1, 1))
    add_face(vertices, uvs, normals, faces,
             [(-1.0, -0.81, 0.81), (-1.0, -0.81, 0.19), (-1.0, -0.19, 0.19), (-1.0, -0.19, 0.81)], (2, 1))
    add_face(vertices, uvs, normals, faces,
             [(2.0, -0.81, -0.81), (2.0, -0.81, -0.19), (2.0, -0.19, -0.19), (2.0, -0.19, -0.81)], (3, 1))
    for center_x, tile in ((0.3, (4, 1)), (0.7, (5, 1))):
        add_face(vertices, uvs, normals, faces,
                 [(center_x - 0.09, 1.626, -0.89), (center_x + 0.09, 1.626, -0.89),
                  (center_x + 0.09, 1.626, -0.71), (center_x - 0.09, 1.626, -0.71)], tile)

    # OBJ-пайплайн отсекает задние грани. Дублируем каждую грань с обратной нормалью:
    # даже тонкие трубы, контакты, стрелки и пластины видны с обеих сторон.
    for face in list(faces):
        reverse = []
        for vi, ti, ni in reversed(face):
            normals.append(tuple(-value for value in normals[ni]))
            reverse.append((vi, ti, len(normals) - 1))
        faces.append(reverse)

    lines = ["mtllib membrane_electrolyzer.mtl", "o membrane_electrolyzer"]
    lines += [f"v {x:.5f} {y:.5f} {z:.5f}" for x, y, z in vertices]
    lines += [f"vt {u:.5f} {v:.5f}" for u, v in uvs]
    lines += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in normals]
    # Без материала загрузчик neoforge:obj не создаёт ни одной грани, а IE basic_split падает на пустой модели
    lines += ["usemtl membrane_electrolyzer"]
    lines += ["f " + " ".join(f"{vi + 1}/{ti + 1}/{ni + 1}" for vi, ti, ni in face) for face in faces]
    OBJ.write_text("\n".join(lines) + "\n")
    print(f"OBJ: {len(faces)} квадов, {len(vertices)} вершин; все грани двусторонние")


def render(view, output, size=512):
    vertices, uvs, normals, faces = parse_obj(OBJ)
    atlas = np.asarray(Image.open(ATLAS).convert("RGBA"), dtype=np.float32) / 255
    forward = {
        "south": (0, 0, -1), "north": (0, 0, 1), "east": (-1, 0, 0),
        "west": (1, 0, 0), "top": (0, -1, 0),
    }[view]
    right = {"south": (1, 0, 0), "north": (-1, 0, 0), "east": (0, 0, 1),
             "west": (0, 0, -1), "top": (1, 0, 0)}[view]
    up = {"top": (0, 0, -1)}.get(view, (0, 1, 0))
    forward, right, up = map(lambda value: np.array(value, dtype=float), (forward, right, up))
    image = np.full((size, size, 3), (0.84, 0.85, 0.84), dtype=np.float32)
    depth = np.full((size, size), np.inf, dtype=np.float32)
    scale = size / 3.7
    center = np.array((0.5, 0.5, 0.0))
    light = np.array((-0.35, 0.8, 0.55))

    for face in faces:
        face_normals = np.array(normals[face[0][2]])
        if np.dot(face_normals, -forward) <= 0:
            continue
        points = np.array([vertices[item[0]] for item in face])
        uv = np.array([uvs[item[1]] for item in face])
        projected = np.column_stack(((points - center) @ right, (points - center) @ up))
        pixels = projected * scale + size / 2
        pixels[:, 1] = size - pixels[:, 1]
        z = (points - center) @ forward
        shade = 0.72 + 0.28 * abs(np.dot(face_normals, light))
        for tri in ((0, 1, 2), (0, 2, 3)):
            p = pixels[list(tri)]
            min_x, min_y = np.maximum(np.floor(p.min(axis=0)).astype(int), 0)
            max_x, max_y = np.minimum(np.ceil(p.max(axis=0)).astype(int), size - 1)
            if min_x > max_x or min_y > max_y:
                continue
            yy, xx = np.mgrid[min_y:max_y + 1, min_x:max_x + 1]
            sample = np.stack((xx + 0.5, yy + 0.5), axis=-1)
            a, b, c = p
            denom = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(denom) < 1e-8:
                continue
            w0 = ((b[1] - c[1]) * (sample[..., 0] - c[0]) + (c[0] - b[0]) * (sample[..., 1] - c[1])) / denom
            w1 = ((c[1] - a[1]) * (sample[..., 0] - c[0]) + (a[0] - c[0]) * (sample[..., 1] - c[1])) / denom
            w2 = 1 - w0 - w1
            mask = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            tri_z = z[list(tri)]
            zz = w0 * tri_z[0] + w1 * tri_z[1] + w2 * tri_z[2]
            mask &= zz < depth[min_y:max_y + 1, min_x:max_x + 1]
            if not mask.any():
                continue
            tri_uv = uv[list(tri)]
            tex = w0[..., None] * tri_uv[0] + w1[..., None] * tri_uv[1] + w2[..., None] * tri_uv[2]
            tx = np.clip((tex[..., 0] * 256).astype(int), 0, 255)
            ty = np.clip(((1 - tex[..., 1]) * 256).astype(int), 0, 255)
            color = atlas[ty, tx, :3] * shade
            target = image[min_y:max_y + 1, min_x:max_x + 1]
            target[mask] = color[mask]
            depth[min_y:max_y + 1, min_x:max_x + 1][mask] = zz[mask]
    Image.fromarray((np.clip(image, 0, 1) * 255).astype(np.uint8)).save(output)


if __name__ == "__main__":
    build_tiles()
    update_geometry()
    for view in ("south", "north", "east", "west", "top"):
        render(view, ROOT / f"electrolyzer-{view}.png")
