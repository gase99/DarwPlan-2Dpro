package ir.cabinetplan.fa.data

import ir.cabinetplan.fa.domain.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON codec for Room persistence.
 * - Writes v2 documents (walls + rich Persian cabinet objects + clientNameFa + notesFa).
 * - Seamlessly upgrades legacy v1 millimeter line/group documents into v2 centimeter geometry.
 * - Rejects unknown versions and invalid coordinates without corrupting open state.
 */
object DocumentCodec {
    private const val MAX_JSON_CHARS = 4_000_000

    fun encode(document: ProjectDocument): String {
        require(document.valid())
        val wallsArr = JSONArray()
        for (w in document.lines) {
            wallsArr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("sx", w.start.x.toDouble())
                    .put("sy", w.start.y.toDouble())
                    .put("ex", w.end.x.toDouble())
                    .put("ey", w.end.y.toDouble())
                    .put("thickness", w.thickness.toDouble())
                    .put("label", w.label)
                    .put("isWall", w.isWall)
            )
        }

        val cabsArr = JSONArray()
        for (c in document.cabinets) {
            cabsArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("catalogId", c.catalogId)
                    .put("symbolType", c.symbolType.name)
                    .put("category", c.category.name)
                    .put("nameFa", c.nameFa)
                    .put("codeFa", c.codeFa)
                    .put("x", c.x.toDouble())
                    .put("y", c.y.toDouble())
                    .put("width", c.width.toDouble())
                    .put("depth", c.depth.toDouble())
                    .put("height", c.height.toDouble())
                    .put("elevationCm", c.elevationCm.toDouble())
                    .put("rotation", c.rotation)
                    .put("fillArgb", c.fillArgb)
                    .put("strokeArgb", c.strokeArgb)
                    .put("doorsCount", c.doorsCount)
                    .put("drawersCount", c.drawersCount)
                    .put("notes", c.notes)
            )
        }

        return JSONObject()
            .put("version", 2)
            .put("clientNameFa", document.clientNameFa)
            .put("notesFa", document.notesFa)
            .put("walls", wallsArr)
            .put("cabinets", cabsArr)
            .toString()
    }

    fun decode(json: String): ProjectDocument {
        require(json.length <= MAX_JSON_CHARS)
        val root = JSONObject(json)
        return when (val version = root.getInt("version")) {
            2 -> decodeV2(root)
            1 -> decodeLegacyV1(root)
            else -> throw IllegalArgumentException("Unsupported document version: $version")
        }
    }

    private fun decodeV2(root: JSONObject): ProjectDocument {
        val wallsJson = root.getJSONArray("walls")
        val cabsJson = root.getJSONArray("cabinets")
        require(wallsJson.length() <= ProjectDocument.MAX_ITEMS)
        require(cabsJson.length() <= ProjectDocument.MAX_ITEMS)

        val walls = (0 until wallsJson.length()).map { i ->
            val obj = wallsJson.getJSONObject(i)
            val id = obj.getString("id").also { require(it.length in 1..128) }
            WallLine(
                id = id,
                start = Point(obj.getDouble("sx").toFloat(), obj.getDouble("sy").toFloat()),
                end = Point(obj.getDouble("ex").toFloat(), obj.getDouble("ey").toFloat()),
                thickness = obj.optDouble("thickness", 15.0).toFloat(),
                label = obj.optString("label", "دیوار سازه‌ای").take(120),
                isWall = obj.optBoolean("isWall", true),
            )
        }

        val cabinets = (0 until cabsJson.length()).map { i ->
            val obj = cabsJson.getJSONObject(i)
            val id = obj.getString("id").also { require(it.length in 1..128) }
            CabinetObject(
                id = id,
                catalogId = obj.optString("catalogId", "custom").take(128),
                symbolType = CabinetSymbolType.valueOf(obj.getString("symbolType")),
                category = CabinetCategory.valueOf(obj.getString("category")),
                nameFa = obj.getString("nameFa").also { require(it.length in 1..160) },
                codeFa = obj.optString("codeFa", "U-01").take(32),
                x = obj.getDouble("x").toFloat(),
                y = obj.getDouble("y").toFloat(),
                width = obj.getDouble("width").toFloat(),
                depth = obj.getDouble("depth").toFloat(),
                height = obj.getDouble("height").toFloat(),
                elevationCm = obj.optDouble("elevationCm", 0.0).toFloat(),
                rotation = obj.optInt("rotation", 0),
                fillArgb = obj.optLong("fillArgb", 0x38E09F3EL),
                strokeArgb = obj.optLong("strokeArgb", 0xFFE09F3EL),
                doorsCount = obj.optInt("doorsCount", 0),
                drawersCount = obj.optInt("drawersCount", 0),
                notes = obj.optString("notes", "").take(500),
            )
        }

        val doc = ProjectDocument(
            lines = walls,
            cabinets = cabinets,
            clientNameFa = root.optString("clientNameFa", "").take(500),
            notesFa = root.optString("notesFa", "").take(4000),
        )
        require(doc.valid())
        return doc
    }

    /** Upgrades legacy v1 millimeter line documents seamlessly into v2 centimeter plans. */
    private fun decodeLegacyV1(root: JSONObject): ProjectDocument {
        val linesArr = root.getJSONArray("lines")
        require(linesArr.length() <= ProjectDocument.MAX_ITEMS)

        data class RawSeg(val id: String, val startCm: Point, val endCm: Point, val group: String?, val kind: String?)

        val rawSegs = (0 until linesArr.length()).map { i ->
            val obj = linesArr.getJSONObject(i)
            val id = obj.getString("id").also { require(it.length in 1..128) }
            val group = if (obj.isNull("group")) null else obj.getString("group")
            val kind = if (obj.isNull("kind")) null else obj.getString("kind")
            require((group == null) == (kind == null))
            RawSeg(
                id = id,
                startCm = Point(obj.getDouble("sx").toFloat() / 10f, obj.getDouble("sy").toFloat() / 10f),
                endCm = Point(obj.getDouble("ex").toFloat() / 10f, obj.getDouble("ey").toFloat() / 10f),
                group = group,
                kind = kind,
            )
        }

        val walls = rawSegs.filter { it.group == null }.map { seg ->
            WallLine(
                id = seg.id,
                start = seg.startCm,
                end = seg.endCm,
                thickness = 15f,
                label = "دیوار",
                isWall = true,
            )
        }

        val cabinets = rawSegs.filter { it.group != null }
            .groupBy { it.group!! }
            .map { (groupId, segs) ->
                val pts = segs.flatMap { listOf(it.startCm, it.endCm) }
                val minX = pts.minOf { it.x }
                val maxX = pts.maxOf { it.x }
                val minY = pts.minOf { it.y }
                val maxY = pts.maxOf { it.y }
                val template = when (segs.first().kind) {
                    "WALL", "SHELF" -> PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.WALL_DOUBLE }
                    "DRAWER" -> PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.BASE_DRAWERS }
                    "DOOR" -> PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.DOOR_SWING }
                    else -> PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.BASE_SINGLE }
                }
                template.createUnit(Point(minX, minY)).copy(
                    id = "cab_$groupId",
                    width = (maxX - minX).coerceAtLeast(10f),
                    depth = (maxY - minY).coerceAtLeast(10f),
                )
            }

        val doc = ProjectDocument(lines = walls, cabinets = cabinets)
        require(doc.valid())
        return doc
    }
}
