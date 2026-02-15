package io.github.devngho.openriro.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * ```json
 * {"code":400,"msg":"아이디가 없거나 비밀번호가 맞지 않습니다. (1\/5회 오류)"}
 * ```
 * ```json
 * {"code":"000","msg":"로그인에 성공했습니다."}
 * ```
 *
 * 이런 상황에서 `code`와 같은 필드를 파싱해내기 위한 Serializer
 */
class PrimitiveAsStringSerializer: KSerializer<String> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("PrimitiveAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val primitive = decoder.decodeSerializableValue(JsonPrimitive.serializer())

        return primitive.content
    }
}