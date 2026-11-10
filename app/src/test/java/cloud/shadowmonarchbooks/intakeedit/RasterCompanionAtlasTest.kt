package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.*
import org.junit.Test

class RasterCompanionAtlasTest {
    private fun rigDoc(
        src: String = "[4,8,32,48]",
        sprites: String = """[{"id":"hair_back","atlas":0,"src":$src,"position":[12,19],"pivot":[30,50],"z":0}]""",
    ): String = """{
      "format":2, "kind":"raster-rig", "canvas":[320,320],
      "atlases":[{"file":"texture-0.webp","width":128,"height":128,"bytes":4000}],
      "sprites":$sprites
    }"""

    @Test fun parsesIndependentLayerWithoutFlattenedImages() {
        val spec = RasterAtlasSpecification.parse(rigDoc())
        assertEquals(listOf("texture-0.webp"), spec.atlasNames)
        assertEquals(1, spec.sprites.size)
        assertEquals("hair_back", spec.sprites[0].id)
        assertEquals(12f, spec.sprites[0].x)
        assertEquals(30f, spec.sprites[0].pivotX)
    }

    @Test fun supportsHighResolutionTexturesWithLogicalSize() {
        val doc = rigDoc().replace("\"pivot\":[30,50]", "\"pivot\":[30,50],\"size\":[16,24]")
        val sprite = RasterAtlasSpecification.parse(doc).sprites.single()
        assertEquals(32, sprite.width)
        assertEquals(16f, sprite.logicalWidth)
        assertEquals(24f, sprite.logicalHeight)
    }

    @Test fun parentTransformChainIncludesAncestorsInOrder() {
        val sprites = """[
            {"id":"hair_back","atlas":0,"src":[0,0,8,8],"position":[0,0],"pivot":[4,4],"z":0,"parent":null},
            {"id":"face","atlas":0,"src":[20,0,8,8],"position":[10,0],"pivot":[14,4],"z":1,"parent":"hair_back"},
            {"id":"eye","atlas":0,"src":[40,0,8,8],"position":[20,0],"pivot":[22,4],"z":2,"parent":"face"}
        ]"""
        val rig = RasterAtlasSpecification.parse(rigDoc(sprites = sprites))
        assertEquals(listOf("hair_back", "face", "eye"),
            rig.poseChain(rig.sprites.last()).map { it.id })
        val circular = sprites.replace("\"z\":0,\"parent\":null", "\"z\":0,\"parent\":\"eye\"")
        assertThrows(IllegalArgumentException::class.java) {
            RasterAtlasSpecification.parse(rigDoc(sprites = circular))
        }
    }

    @Test fun rejectsOutOfBoundsTextureRect() {
        assertThrows(IllegalArgumentException::class.java) {
            RasterAtlasSpecification.parse(rigDoc(src = "[120,120,32,48]"))
        }
    }

    @Test fun rejectsDuplicateOrFlatFakeLayers() {
        val duplicate = """[{"id":"hair_back","atlas":0,"src":[0,0,10,10],"position":[0,0],"pivot":[0,0],"z":0},
          {"id":"hair_back","atlas":0,"src":[20,0,10,10],"position":[0,0],"pivot":[0,0],"z":1}]"""
        assertThrows(IllegalArgumentException::class.java) {
            RasterAtlasSpecification.parse(rigDoc(sprites = duplicate))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RasterAtlasSpecification.parse(rigDoc(sprites = "[]"))
        }
    }

    @Test fun rejectsUnsafeAtlasPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            RasterAtlasSpecification.parse(rigDoc().replace("texture-0.webp", "../bad.webp"))
        }
    }
}
