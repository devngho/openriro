package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroClient

interface Request<Req, Res> {
    suspend fun execute(client: OpenRiroClient, request: Req): Result<Res>
}

interface JSONResponse {
    val code: String
    val msg: String
}