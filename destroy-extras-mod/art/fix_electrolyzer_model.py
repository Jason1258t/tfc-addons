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

PORT_COLORS = ((91, 220, 238), (244, 196, 48), (183, 104, 220), (64, 139, 230))
STEEL = (0, 0)
BORE = (10, 1)


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
    # Полпикселя до края, чтобы не захватить соседнюю плитку атласа.
    return ((col * 16 + 0.5) / 256, 1 - (row * 16 + 15.5) / 256), (
        (col * 16 + 15.5) / 256, 1 - (row * 16 + 0.5) / 256)


def build_tiles():
    atlas = Image.open(ORIGINAL_ATLAS).convert("RGBA")
    draw = ImageDraw.Draw(atlas)
    # Плитки исходных стрелок используются и на корпусах. Заменяем их
    # металлом, оставляя цвет только на ободах рабочих портов.
    for col, source in enumerate((0, 4, 2, 0, 0, 0)):
        atlas.paste(atlas.crop((source * 16, 0, source * 16 + 16, 16)), (col * 16, 16))
    for col, color in enumerate(PORT_COLORS, start=6):
        x = col * 16
        draw.rectangle((x, 16, x + 15, 31), fill=color)
    draw.rectangle((BORE[0] * 16, 16, BORE[0] * 16 + 15, 31), fill=(29, 34, 38))
    for col, sign, color in ((11, "+", (190, 55, 48)), (12, "−", (59, 65, 73))):
        x, y = col * 16, 16
        draw.rectangle((x, y, x + 15, y + 15), fill=color)
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


def port_faces(mouth, axis, radial, length, color):
    """Патрубок от корпуса до границы блока, с заглублённым тёмным каналом."""
    axis = np.array(axis)
    radial = np.array(radial)
    tangent = np.cross(axis, radial)

    def ring(depth, radius):
        return [
            tuple(np.array(mouth) - depth * axis
                  + radius * (math.cos(angle) * radial + math.sin(angle) * tangent))
            for angle in (math.pi / 8 + i * math.pi / 4 for i in range(8))
        ]

    def strip(first, second, tile):
        for i in range(8):
            j = (i + 1) % 8
            yield (first[i], first[j], second[j], second[i]), tile

    neck = ring(length, 0.15)
    shoulder = ring(0.075, 0.15)
    flange_base = ring(0.075, 0.2375)
    flange = ring(0.018, 0.2375)
    rim = ring(0, 0.21875)
    opening = ring(0, 0.134)
    recess = ring(0.037, 0.134)
    yield from strip(neck, shoulder, STEEL)
    yield from strip(shoulder, flange_base, STEEL)
    yield from strip(flange_base, flange, STEEL)
    yield from strip(flange, rim, color)
    yield from strip(rim, opening, color)
    yield from strip(opening, recess, BORE)
    for indices in ((0, 1, 2, 3), (0, 3, 4, 7), (4, 5, 6, 7)):
        yield [recess[i] for i in indices], BORE


def update_geometry():
    vertices, uvs, normals, original_faces = parse_obj(ORIGINAL)
    faces = []
    for face in original_faces:
        points = [vertices[item[0]] for item in face]
        # Снимаем старые наконечники целиком, включая разорванные участки труб.
        gas_neck = any(all(abs(x - center_x) < 0.27 and y >= 1.7 and abs(z - 0.5) < 0.27
                           for x, y, z in points) for center_x in (-0.5, 1.5))
        catholyte_neck = all(x <= -0.78 and abs(y + 0.5) < 0.27 and abs(z - 0.5) < 0.27
                             for x, y, z in points)
        input_neck = all(x >= 1.62 and abs(y + 0.5) < 0.27 and abs(z + 0.5) < 0.27
                         for x, y, z in points)
        terminal_cap = all(y == 1.9 for _, y, _ in points)
        terminal_plate = all(y == 1.635 and 0.23 <= x <= 0.77 and -0.75 <= z <= -0.63
                             for x, y, z in points)
        if gas_neck or catholyte_neck or input_neck or terminal_cap or terminal_plate:
            continue
        # Торцы горизонтальных коллекторов остаются глухими.
        collector_end = any(
            all(abs(abs(z) - 0.9) < 1e-6 and abs(x - center_x) <= 0.325 and abs(y - 1.55) <= 0.325
                for x, y, z in points) for center_x in (-0.5, 1.5)
        )
        if collector_end:
            # Сохраняем дисковую геометрию торца и подменяем UV металлической плиткой.
            uvs.append((0.03125, 0.96875))
            face = [(vi, len(uvs) - 1, ni) for vi, _, ni in face]
        faces.append(face)

    # Доли блока оставляют торцы внутри клетки IE basic_split.
    ports = (
        ((-0.5, 1.999, 0.5), (0, 1, 0), (1, 0, 0), 0.18),
        ((1.5, 1.999, 0.5), (0, 1, 0), (1, 0, 0), 0.18),
        ((-0.999, -0.5, 0.5), (-1, 0, 0), (0, 1, 0), 0.14),
        ((1.999, -0.5, -0.5), (1, 0, 0), (0, 1, 0), 0.41),
    )
    for col, (mouth, axis, radial, length) in enumerate(ports, start=6):
        for points, tile in port_faces(mouth, axis, radial, length, (col, 1)):
            add_face(vertices, uvs, normals, faces, points, tile)

    # Стояки соединяют коллекторы с баками; торцы скрыты внутри корпусов.
    for x in (-0.5, 1.5):
        for z in (-0.5, 0.5):
            footprint = [
                (x + 0.105 * math.cos(angle), z + 0.105 * math.sin(angle))
                for angle in (math.pi / 8 + i * math.pi / 4 for i in range(8))
            ]
            for i, (x0, z0) in enumerate(footprint):
                x1, z1 = footprint[(i + 1) % len(footprint)]
                add_face(vertices, uvs, normals, faces, [
                    (x0, 0.92, z0), (x0, 1.29, z0), (x1, 1.29, z1), (x1, 0.92, z1),
                ], STEEL)

    # Непрозрачный верх контакта совпадает с вершиной подключения провода EE.
    for center_x, col in ((0.3, 11), (0.7, 12)):
        for y, radius, tile in ((1.9, 0.04157, (col, 1)), (1.635, 0.06, (col, 1))):
            z = -0.5 if y == 1.9 else -0.69
            add_face(vertices, uvs, normals, faces, [
                (center_x - radius, y, z + radius), (center_x + radius, y, z + radius),
                (center_x + radius, y, z - radius), (center_x - radius, y, z - radius),
            ], tile)

    # Оставляем обратные грани для тонких панелей исходной модели.
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
            mask &= atlas[ty, tx, 3] > 0.5
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
