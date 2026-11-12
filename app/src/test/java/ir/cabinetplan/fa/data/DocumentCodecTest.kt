package ir.cabinetplan.fa.data

import ir.cabinetplan.fa.domain.*
import org.junit.Assert.*
import org.junit.Test

class DocumentCodecTest {
    @Test fun `versioned JSON round trips symbols and fractional negative coordinates`() {
        val document = PlanDocument(CabinetKind.DRAWER.create(Point(-123.5f, 98.75f)) +
            PlanLine(start = Point(0.5f, -1.25f), end = Point(100f, 200f)))
        assertEquals(document, DocumentCodec.decode(DocumentCodec.encode(document)))
    }
    @Test fun `empty document round trips`() {
        assertEquals(PlanDocument(), DocumentCodec.decode(DocumentCodec.encode(PlanDocument())))
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown format is rejected`() {
        DocumentCodec.decode("""{"version":2,"lines":[]}""")
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate ids are rejected`() {
        val json = """{"id":"a","sx":0,"sy":0,"ex":1,"ey":1,"group":null,"kind":null}"""
        DocumentCodec.decode("""{"version":1,"lines":[$json,$json]}""")
    }
    @Test(expected = IllegalArgumentException::class) fun `unbounded coordinates rejected`() {
        DocumentCodec.decode("""{"version":1,"lines":[{"id":"a","sx":999999,"sy":0,"ex":1,"ey":1,"group":null,"kind":null}]}""")
    }
}
