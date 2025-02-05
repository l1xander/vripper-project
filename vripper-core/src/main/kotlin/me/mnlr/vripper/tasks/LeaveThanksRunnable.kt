package me.mnlr.vripper.tasks

import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import me.mnlr.vripper.SpringContext
import me.mnlr.vripper.delegate.LoggerDelegate
import me.mnlr.vripper.entities.LogEvent
import me.mnlr.vripper.entities.LogEvent.Status.*
import me.mnlr.vripper.entities.PostDownloadState
import me.mnlr.vripper.formatToString
import me.mnlr.vripper.repositories.LogEventRepository
import me.mnlr.vripper.services.HTTPService
import me.mnlr.vripper.services.SettingsService
import java.io.UnsupportedEncodingException

class LeaveThanksRunnable(
    private val postDownloadState: PostDownloadState,
    private val authenticated: Boolean,
    private val context: HttpClientContext
) : Runnable {
    private val log by LoggerDelegate()
    private val cm: HTTPService = SpringContext.getBean(HTTPService::class.java)
    private val settingsService: SettingsService = SpringContext.getBean(SettingsService::class.java)
    private val eventRepository: LogEventRepository = SpringContext.getBean(LogEventRepository::class.java)
    private val logEvent: LogEvent

    init {
        logEvent = eventRepository.save(
            LogEvent(
                type = LogEvent.Type.THANKS, status = PENDING, message = "Leaving thanks for $postDownloadState.url"
            )
        )
    }

    override fun run() {
        try {
            eventRepository.update(logEvent.copy(status = PROCESSING))
            if (!settingsService.settings.viperSettings.login) {
                eventRepository.update(
                    logEvent.copy(
                        status = DONE,
                        message = "Will not send a like for ${postDownloadState.url}\nAuthentication with ViperGirls option is disabled"
                    )
                )
                return
            }
            if (!settingsService.settings.viperSettings.thanks) {
                eventRepository.update(
                    logEvent.copy(
                        status = DONE,
                        message = "Will not send a like for ${postDownloadState.url}\nLeave thanks option is disabled"
                    )
                )
                return
            }
            if (!authenticated) {
                eventRepository.update(
                    logEvent.copy(
                        status = ERROR,
                        message = "Will not send a like for ${postDownloadState.url}\nYou are not authenticated"
                    )
                )
                return
            }
            val postThanks: HttpPost = cm.buildHttpPost(
                "${settingsService.settings.viperSettings.host}/post_thanks.php", HttpClientContext.create()
            )
            val params: MutableList<NameValuePair> = ArrayList()
            params.add(BasicNameValuePair("do", "post_thanks_add"))
            params.add(BasicNameValuePair("using_ajax", "1"))
            params.add(BasicNameValuePair("p", postDownloadState.postId))
            params.add(BasicNameValuePair("securitytoken", postDownloadState.token))
            try {
                postThanks.entity = UrlEncodedFormEntity(params)
            } catch (e: UnsupportedEncodingException) {
                val error = "Request error for ${postDownloadState.url}"
                log.error(error, e)
                eventRepository.update(
                    logEvent.copy(
                        status = ERROR, message = """
                    $error
                    ${e.formatToString()}
                    """.trimIndent()
                    )
                )
                return
            }
            postThanks.addHeader("Referer", settingsService.settings.viperSettings.host)
            postThanks.addHeader(
                "Host", settingsService.settings.viperSettings.host.replace("https://", "").replace("http://", "")
            )
            val client: CloseableHttpClient = cm.client.build()
            client.execute(postThanks, context).use { response ->
                try {
                } finally {
                    EntityUtils.consumeQuietly(response.entity)
                }
            }
            eventRepository.update(logEvent.copy(status = DONE))
        } catch (e: Exception) {
            val error = "Failed to leave a thanks for $postDownloadState"
            log.error(error, e)
            eventRepository.update(
                logEvent.copy(
                    status = ERROR, message = """
                $error
                ${e.formatToString()}
                """.trimIndent()
                )
            )
        }
    }
}