package io.github.devngho.openriro.endpoints

import io.github.devngho.openriro.client.OpenRiroAPI

interface Request<Req, Res> {
    suspend fun execute(client: OpenRiroAPI, request: Req): Result<Res>
}

interface JSONResponse {
    val code: String
    val msg: String
}