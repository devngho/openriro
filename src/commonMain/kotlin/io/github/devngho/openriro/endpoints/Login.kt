package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.AuthConfig
import io.github.devngho.openriro.client.OpenRiroClient
import io.github.devngho.openriro.common.InternalApi
import io.github.devngho.openriro.client.OpenRiroClientImpl
import io.github.devngho.openriro.client.json
import io.github.devngho.openriro.common.LoginFailedException
import io.github.devngho.openriro.util.PrimitiveAsStringSerializer
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@InternalApi
/**
 * 로그인 요청
 *
 * 이 요청을 사용하는 대신 [OpenRiroClientImpl]과 `login`을 사용하는 것을 권장합니다.
 */
class Login: Request<AuthConfig, Login.LoginResponse> {
    @Serializable
    data class LoginResponse(
        @Serializable(PrimitiveAsStringSerializer::class) override val code: String,
        override val msg: String,
    ): JSONResponse

    override suspend fun execute(client: OpenRiroClient, request: AuthConfig): Result<LoginResponse> = client.retry {
        val res = client.httpClient.post("${client.config.baseUrl}/ajax.php") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("app", "user")
                append("mode", "login")
                append("userType", request.userType.value)
                append("id", request.id)
                append("pw", request.pw)
            }))
        }

        val body = json.decodeFromString<LoginResponse>(res.bodyAsText())

        if (body.code != "000") {
            throw LoginFailedException(body.msg)
        }

        body
    }
}