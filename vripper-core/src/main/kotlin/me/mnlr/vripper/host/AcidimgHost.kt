package me.mnlr.vripper.host

import org.apache.http.NameValuePair
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.springframework.stereotype.Service
import org.w3c.dom.Document
import org.w3c.dom.Node
import me.mnlr.vripper.delegate.LoggerDelegate
import me.mnlr.vripper.download.ImageDownloadContext
import me.mnlr.vripper.exception.HostException
import me.mnlr.vripper.exception.XpathException
import me.mnlr.vripper.services.*

@Service
class AcidimgHost(
    private val httpService: HTTPService,
    private val htmlProcessorService: HtmlProcessorService,
    private val xpathService: XpathService,
    dataTransaction: DataTransaction,
    downloadSpeedService: DownloadSpeedService,
) : Host(httpService, htmlProcessorService, dataTransaction, downloadSpeedService) {
    private val log by LoggerDelegate()
    override val host: String
        get() = Companion.host

    @Throws(HostException::class)
    override fun resolve(
        url: String,
        document: Document,
        context: ImageDownloadContext
    ): Pair<String, String> {
        try {
            log.debug(
                String.format(
                    "Looking for xpath expression %s in %s",
                    CONTINUE_BUTTON_XPATH,
                    url
                )
            )
            xpathService.getAsNode(document, CONTINUE_BUTTON_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        }
        log.debug(String.format("Click button found for %s", url))
        val client: HttpClient = httpService.client.build()
        val httpPost = httpService.buildHttpPost(url, context.httpContext)
        httpPost.addHeader("Referer", url)
        val params: MutableList<NameValuePair> = ArrayList()
        params.add(BasicNameValuePair("imgContinue", "Continue to your image"))
        try {
            httpPost.entity = UrlEncodedFormEntity(params)
        } catch (e: Exception) {
            throw HostException(e)
        }
        log.debug(String.format("Requesting %s", httpPost))
        val doc = try {
            (client.execute(
                httpPost, context.httpContext
            ) as CloseableHttpResponse).use { response ->
                log.debug(String.format("Cleaning response for %s", httpPost))
                try {
                    htmlProcessorService.clean(response.entity.content)
                } finally {
                    EntityUtils.consumeQuietly(response.entity)
                }
            }
        } catch (e: Exception) {
            throw HostException(e)
        }
        val imgNode: Node = try {
            log.debug(String.format("Looking for xpath expression %s in %s", IMG_XPATH, url))
            xpathService.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e)
        } ?: throw HostException(
            String.format(
                "Xpath '%s' cannot be found in '%s'",
                IMG_XPATH,
                url
            )
        )
        return try {
            log.debug(String.format("Resolving name and image url for %s", url))
            val imgTitle = imgNode.attributes.getNamedItem("alt").textContent.trim()
            val imgUrl = imgNode.attributes.getNamedItem("src").textContent.trim()
            Pair(
                imgTitle.ifEmpty { getDefaultImageName(imgUrl) }, imgUrl
            )
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val host = "acidimg.cc"
        private const val lookup = "acidimg.cc"
        private const val CONTINUE_BUTTON_XPATH = "//input[@id='continuebutton']"
        private const val IMG_XPATH = "//img[@class='centred']"
    }
}