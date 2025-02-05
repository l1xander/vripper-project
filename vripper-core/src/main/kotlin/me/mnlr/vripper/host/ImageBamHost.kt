package me.mnlr.vripper.host

import org.apache.http.impl.cookie.BasicClientCookie
import org.springframework.stereotype.Service
import org.w3c.dom.Document
import org.w3c.dom.Node
import me.mnlr.vripper.delegate.LoggerDelegate
import me.mnlr.vripper.download.ImageDownloadContext
import me.mnlr.vripper.exception.HostException
import me.mnlr.vripper.exception.XpathException
import me.mnlr.vripper.services.*
import java.sql.Date
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
class ImageBamHost(
    private val htmlProcessorService: HtmlProcessorService,
    private val xpathService: XpathService,
    httpService: HTTPService,
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
        val doc = try {
            log.debug(String.format("Looking for xpath expression %s in %s", CONTINUE_XPATH, url))
            if (xpathService.getAsNode(document, CONTINUE_XPATH) != null) {
                val clientCookie = BasicClientCookie("nsfw_inter", "1")
                clientCookie.domain = "www.imagebam.com"
                clientCookie.path = "/"
                clientCookie.expiryDate =
                    Date.from(
                        LocalDateTime.now().plusDays(3).atZone(ZoneId.systemDefault()).toInstant()
                    )
                context.httpContext.cookieStore.addCookie(clientCookie)
                fetch(url, context) {
                    htmlProcessorService.clean(it.entity.content)
                }
            } else {
                document
            }
        } catch (e: XpathException) {
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
            val imgTitle = Optional.ofNullable(imgNode.attributes.getNamedItem("alt"))
                .map { e: Node -> e.textContent.trim { it <= ' ' } }
                .orElse("")
            val imgUrl = Optional.ofNullable(imgNode.attributes.getNamedItem("src"))
                .map { e: Node -> e.textContent.trim { it <= ' ' } }
                .orElse("")
            var defaultName: String = UUID.randomUUID().toString()
            val index = imgUrl.lastIndexOf('/')
            if (index != -1 && index < imgUrl.length) {
                defaultName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1)
            }
            Pair((imgTitle.ifEmpty { defaultName })!!, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred", e)
        }
    }

    companion object {
        private const val host = "imagebam.com"
        private const val lookup = "imagebam.com"
        private const val IMG_XPATH = "//img[contains(@class,'main-image')]"
        private const val CONTINUE_XPATH = "//*[contains(text(), 'Continue')]"
    }
}