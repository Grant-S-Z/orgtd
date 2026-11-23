package dev.orgtd.data

import java.io.IOException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = WebDavClient(server.url("/gtd.org").toString(), "me@example.com", "app-password")

    @Test
    fun `读取文件并带回 ETag`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"v1\"")
                .setBody("* TODO 写周报\n"),
        )

        val file = client().get()

        assertEquals("* TODO 写周报\n", file?.text)
        assertEquals("\"v1\"", file?.etag)
    }

    @Test
    fun `文件不存在返回 null`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(client().get())
    }

    @Test
    fun `写回时带上 If-Match`() {
        server.enqueue(MockResponse().setResponseCode(204).addHeader("ETag", "\"v2\""))

        val updated = client().put("* TODO 写周报\n* DONE 交报销\n", "\"v1\"", null)

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("\"v1\"", recorded.getHeader("If-Match"))
        assertEquals("\"v2\"", updated?.etag)
        assertTrue(recorded.body.readUtf8().contains("DONE 交报销"))
    }

    @Test
    fun `服务端拒绝时抛冲突异常`() {
        server.enqueue(MockResponse().setResponseCode(412))

        assertThrows(ConflictException::class.java) {
            client().put("* TODO x\n", "\"old\"", null)
        }
    }

    @Test
    fun `网络错误抛 IOException`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(IOException::class.java) {
            client().get()
        }
    }
}
