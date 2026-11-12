package ir.cabinetplan.fa.data

import ir.cabinetplan.fa.domain.*
import org.junit.Assert.*
import org.junit.Test

class DocumentCodecTest {

    @Test
    fun `v2 JSON round trips preset L kitchen with walls cabinets and client metadata`() {
        val original = PRESET_PROJECTS.first().document
        val encoded = DocumentCodec.encode(original)
        val decoded = DocumentCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `empty document round trips`() {
        val empty = ProjectDocument()
        assertEquals(empty, DocumentCodec.decode(DocumentCodec.encode(empty)))
    }

    @Test
    fun `legacy v1 millimeter document migrates seamlessly to centimeter walls and cabinets`() {
        val legacyJson = """
            {
              "version": 1,
              "lines": [
                {"id":"w1","sx":0,"sy":0,"ex":3000,"ey":0,"group":null,"kind":null},
                {"id":"c1","sx":100,"sy":100,"ex":700,"ey":100,"group":"g1","kind":"BASE"},
                {"id":"c2","sx":700,"sy":100,"ex":700,"ey":700,"group":"g1","kind":"BASE"}
              ]
            }
        """.trimIndent()

        val migrated = DocumentCodec.decode(legacyJson)
        assertEquals(1, migrated.lines.size)
        assertEquals(300f, migrated.lines.first().lengthCm, 0.01f)
        assertEquals(1, migrated.cabinets.size)
        assertEquals(60f, migrated.cabinets.first().width, 0.01f)
        assertEquals(60f, migrated.cabinets.first().depth, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown version is rejected`() {
        DocumentCodec.decode("""{"version":99,"walls":[],"cabinets":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate wall ids are rejected`() {
        val w = """{"id":"dup","sx":0,"sy":0,"ex":100,"ey":0,"thickness":15,"label":"دیوار","isWall":true}"""
        DocumentCodec.decode("""{"version":2,"walls":[$w,$w],"cabinets":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unbounded coordinates are rejected`() {
        val w = """{"id":"far","sx":999999,"sy":0,"ex":100,"ey":0,"thickness":15,"label":"دیوار","isWall":true}"""
        DocumentCodec.decode("""{"version":2,"walls":[$w],"cabinets":[]}""")
    }
}
