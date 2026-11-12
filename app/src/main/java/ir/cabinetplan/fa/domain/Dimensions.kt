package ir.cabinetplan.fa.domain

/** Accept both Persian/Arabic and Latin digits, without guessing ambiguous comma decimals. */
fun parseMillimeters(text: String): Float? {
    val normalized = text.trim().map { c -> when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        '٫' -> '.'
        else -> c
    } }.joinToString("").replace("٬", "")
    return normalized.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.1f..100000f }
}

fun EditorState.resizeLength(length: Float): EditorState {
    if (!length.isFinite() || length !in 0.1f..100000f) return this
    val line = document.lines.singleOrNull { it.id in selected }?.takeIf { it.group == null } ?: return this
    val next = line.copy(end = line.start + (line.end - line.start) * (length / line.length))
    return commit(PlanDocument(document.lines.map { if (it.id == line.id) next else it }))
}

fun EditorState.resizeBounds(width: Float, height: Float): EditorState {
    if (!width.isFinite() || !height.isFinite() || width !in 0.1f..100000f || height !in 0.1f..100000f) return this
    val lines = document.lines.filter { it.id in selected }
    val bounds = PlanDocument(lines).bounds ?: return this
    if (bounds.width < 0.1f || bounds.height < 0.1f) return this
    fun resize(p: Point) = Point(bounds.center.x + (p.x - bounds.center.x) * width / bounds.width,
        bounds.center.y + (p.y - bounds.center.y) * height / bounds.height)
    return commit(PlanDocument(document.lines.map {
        if (it.id in selected) it.copy(start = resize(it.start), end = resize(it.end)) else it
    }))
}
