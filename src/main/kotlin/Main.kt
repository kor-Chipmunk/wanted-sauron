import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import java.net.URL
import com.github.kittinunf.fuel.httpPost
import com.google.gson.reflect.TypeToken

fun main(args: Array<String>) {
    val jobSearchApiUrl = WANTED_JOB_SEARCH_API_URL

    val rawResponse = URL(jobSearchApiUrl).readText()
    val response = Gson().fromJson(rawResponse, Response::class.java)

    val latestViewedJobId = Gson().fromJson<List<ViewData>>(
        LAST_VIEWED_ID_API_URL.httpGet()
            .header("x-apikey", System.getenv(ENV_KEY_REST_DB_KEY))
            .string(),
        object : TypeToken<List<ViewData>>() {}.type
    ).firstOrNull()?.jobId
    val unCheckedCompanyList = response.data
        .takeWhile { it.id != latestViewedJobId }

    unCheckedCompanyList.onEach { data ->
        val message = ":bell: 띵동! 새로운 %s 포지션이 생겼습니다.\n회사 : %s\n포지션 : %s\nhttps://www.wanted.co.kr/wd/%d"
            .format(POSITION_NAME, data.company.name, data.position, data.id)

        val webHookData = WebHookData(
            username = "원티드 채용봇",
            avatar_url = "https://static.wanted.co.kr/images/wdes/0_4.9f31b341.jpg",
            allowed_mentions = AllowedMentions(
                parse = listOf("users", "roles")
            ),
            embeds = listOf(Embed(
                color = "38912",
                author = Author(
                    name = data.company.name,
                    url = "https://www.wanted.co.kr/company/%d".format(data.company.id),
                    icon_url = data.logo_img.thumb
                ),
                title = data.position,
                url = "https://www.wanted.co.kr/wd/%d".format(data.id),
                thumbnail = Image(url = data.title_img.thumb),
                description = message
            ))
        )

        for (webhook in ENV_KEY_DISCORD_WEBHOOKS) {
            System.getenv(webhook).httpPost()
                .header(Headers.CONTENT_TYPE, "application/json")
                .body(Gson().toJson(webHookData))
                .response()
        }
    }.firstOrNull()
        ?.let {
            LAST_VIEWED_ID_API_URL.httpPost(listOf("jobId" to it.id))
                .header("x-apikey", System.getenv(ENV_KEY_REST_DB_KEY))
                .response()
        }

}

fun Request.string() = responseString().third.component1()

data class ViewData(val jobId: Int?)

data class Response(val data: List<Data>)

data class Data(
    val id: Int,
    val position: String,
    val company: Company,
    val address: Address,
    val title_img: OriginThumbImage,
    val logo_img: OriginThumbImage
)

data class Company(
    val id: Int,
    val name: String
)

data class Address(val location: String)

data class OriginThumbImage(
    val origin: String,
    val thumb: String
)

data class WebHookData(
    val username: String,
    val avatar_url: String,
    val allowed_mentions: AllowedMentions,
    val embeds: List<Embed>
)

data class AllowedMentions(val parse: List<String>)

data class Embed(
    val color: String,
    val author: Author,
    val title: String,
    val url: String,
    val thumbnail: Image,
    val description: String
)

data class Author(
    val name: String,
    val url: String,
    val icon_url: String
)

data class Image(val url: String)

const val CATEGORY = 669
const val COLLECTION = "front-jobs"
const val POSITION_NAME = "프론트엔드 개발자"

const val WANTED_JOB_SEARCH_API_URL = "https://www.wanted.co.kr/api/v4/jobs?1617705029342&country=kr&tag_type_id=$CATEGORY&job_sort=job.latest_order&locations=all&years=-1"
const val LAST_VIEWED_ID_API_URL = "https://wantedsauron-cb29.restdb.io/rest/$COLLECTION?sort=_id&dir=-1"

val ENV_KEY_DISCORD_WEBHOOKS = arrayOf("DISCORD_WEBHOOK", "DISCORD_FRONT_WEBHOOK")
const val ENV_KEY_REST_DB_KEY = "REST_DB_KEY"