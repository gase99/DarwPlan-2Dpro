package ir.cabinetplan.fa.data

import ir.cabinetplan.fa.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned format; unknown versions and invalid geometry never replace the open document. */
object DocumentCodec {
    fun encode(document: PlanDocument): String {
        require(document.valid())
        val lines = JSONArray()
        document.lines.forEach { line ->
            lines.put(JSONObject().put("id", line.id).put("sx", line.start.x.toDouble()).put("sy", line.start.y.toDouble())
                .put("ex", line.end.x.toDouble()).put("ey", line.end.y.toDouble())
                .put("group", line.group ?: JSONObject.NULL).put("kind", line.kind?.name ?: JSONObject.NULL))
        }
        return JSONObject().put("version", 1).put("lines", lines).toString()
    }

    fun decode(json: String): PlanDocument {
        require(json.length <= 4_000_000)
        val root = JSONObject(json)
        require(root.getInt("version") == 1)
        val lines = root.getJSONArray("lines")
        require(lines.length() <= PlanDocument.MAX_LINES)
        val document = PlanDocument((0 until lines.length()).map { i ->
            val obj = lines.getJSONObject(i)
            val id = obj.getString("id").also { require(it.length in 1..128) }
            val group = if (obj.isNull("group")) null else obj.getString("group").also { require(it.length in 1..128) }
            val kind = if (obj.isNull("kind")) null else CabinetKind.valueOf(obj.getString("kind"))
            require((group == null) == (kind == null))
            PlanLine(id, Point(obj.getDouble("sx").toFloat(), obj.getDouble("sy").toFloat()),
                Point(obj.getDouble("ex").toFloat(), obj.getDouble("ey").toFloat()), group, kind)
        })
        require(document.valid())
        return document
    }
}
