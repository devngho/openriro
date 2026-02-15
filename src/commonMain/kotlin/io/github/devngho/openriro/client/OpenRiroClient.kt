package io.github.devngho.openriro.client

import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.common.LoginFailedException
import io.github.devngho.openriro.endpoints.Login
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal val json = Json {
    ignoreUnknownKeys = true
}

abstract class OpenRiroClient {
    abstract val auth: AuthConfig

    @InternalApi
    abstract var cookies: CookiesStorage

    abstract val httpClient: HttpClient

    abstract val config: RequestConfig

    internal val loggingMutex = Mutex()

    @OptIn(InternalApi::class)
    suspend fun login(force: Boolean = false): Result<Unit> = runCatching {
        loggingMutex.withLock {
            if (cookies.get(Url(config.baseUrl))
                    .find { it.name == "cookie_token" } != null && !force
            ) return@runCatching

            val loginRequest = Login()
            val result = loginRequest.execute(this, auth)

            result.getOrThrow().let {
                if (it.code != "000") {
                    throw LoginFailedException(it.msg)
                }
            }
        }
    }

    @OptIn(InternalApi::class)
    internal suspend fun auth(resp: HttpResponse) = this.also {
        if (cookies.get(Url(config.baseUrl))
                .find { it.name == "cookie_token" } == null || (resp.status == HttpStatusCode.Found && resp.headers["location"] == "/user.php?action=user_logout")) {
            login(true).getOrThrow()

            throw Exception("Session expired")
        }
    }

    suspend fun <T> retry(
        block: suspend () -> T,
    ): Result<T> = runCatching {
        if (this.config.defaultRetryCount <= 0) throw IllegalArgumentException("defaultRetryCount must be greater than 0")

        repeat(this.config.defaultRetryCount - 1) {
            try {
                return@runCatching block()
            } catch (e: Exception) {
                if (e is LoginFailedException) throw e // login info is wrong
            }
        }

        block()
    }

    companion object {
        operator fun invoke(
            auth: AuthConfig,
            config: RequestConfig,
        ): OpenRiroClient = OpenRiroClientImpl(auth, config)
    }
}

class OpenRiroClientImpl(
    override val auth: AuthConfig,
    override val config: RequestConfig,
) : OpenRiroClient() {
    @InternalApi
    override var cookies: CookiesStorage = AcceptAllCookiesStorage()

    @OptIn(InternalApi::class)
    override val httpClient = HttpClient {
//        install(Logging) {
//            logger = Logger.DEFAULT
//            level = LogLevel.HEADERS
//        }
        install(HttpCookies) {
            storage = cookies
        }

        followRedirects = false
    }
}

/**
 * 학교 정보와 같은 공통 정보
 */
data class RequestConfig(
    /**
     * https://(school).riroschool.kr
     */
    val baseUrl: String,
    /**
     * 기본 재시도 횟수입니다. 요청이 실패할 경우 최대 이 횟수만큼 재시도합니다. 기본값은 3회입니다.
     */
    val defaultRetryCount: Int = 3
)

/**
 * 로그인에 사용할 아이디 또는 이메일과, 비밀번호 등 인증 정보
 */
data class AuthConfig(
    val userType: UserType,
    val id: String,
    val pw: String,
)

enum class UserType(val value: String) {
    STUDENT_OR_TEACHER("1"),
    PARENT("2"),
}