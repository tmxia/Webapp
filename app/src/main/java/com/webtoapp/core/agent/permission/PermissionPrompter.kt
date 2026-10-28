package com.webtoapp.core.agent.permission

import com.webtoapp.core.logging.AppLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class PermissionPrompter {

    
    
    
    
    
    private val requestChannel = Channel<PermissionRequest>(Channel.UNLIMITED)
    val requests: Flow<PermissionRequest> = requestChannel.receiveAsFlow()

    private val choiceChannel = Channel<ChoiceRequest>(Channel.UNLIMITED)
    val choices: Flow<ChoiceRequest> = choiceChannel.receiveAsFlow()

    private val responseChannel = Channel<Pair<String, PermissionResponse>>(Channel.UNLIMITED)
    private val choiceResponseChannel = Channel<Pair<String, ChoiceResponse>>(Channel.UNLIMITED)

    private val singleFlight = Mutex()

    suspend fun request(req: PermissionRequest): PermissionResponse = singleFlight.withLock {
        requestChannel.send(req)

        
        
        
        val resp = withTimeoutOrNull(PROMPT_TIMEOUT_MS) {
            while (true) {
                val (id, r) = responseChannel.receive()
                if (id == req.toolCallId) return@withTimeoutOrNull r
            }
            @Suppress("UNREACHABLE_CODE") PermissionResponse.Deny
        }
        resp ?: run {
            AppLogger.w(TAG, "permission request '${req.toolCallId}' timed out after ${PROMPT_TIMEOUT_MS}ms — denying")
            PermissionResponse.Deny
        }
    }

    fun respond(toolCallId: String, response: PermissionResponse) {
        responseChannel.trySend(toolCallId to response)
    }

    suspend fun askChoice(req: ChoiceRequest): ChoiceResponse = singleFlight.withLock {
        choiceChannel.send(req)
        
        
        val resp = withTimeoutOrNull(PROMPT_TIMEOUT_MS) {
            while (true) {
                val (id, r) = choiceResponseChannel.receive()
                if (id == req.id) return@withTimeoutOrNull r
            }
            @Suppress("UNREACHABLE_CODE") ChoiceResponse.Cancelled
        }
        resp ?: run {
            AppLogger.w(TAG, "choice request '${req.id}' timed out after ${PROMPT_TIMEOUT_MS}ms — cancelling")
            ChoiceResponse.Cancelled
        }
    }

    fun respondChoice(requestId: String, response: ChoiceResponse) {
        choiceResponseChannel.trySend(requestId to response)
    }

    companion object {
        private const val TAG = "PermissionPrompter"
        
        
        private const val PROMPT_TIMEOUT_MS = 10L * 60 * 1000
    }
}

data class ChoiceRequest(
    val id: String,
    val questions: List<Question>
) {
    data class Question(
        val text: String,
        val options: List<Option>,
        val multiSelect: Boolean = false,
        val allowOther: Boolean = true
    )

    data class Option(
        val label: String,
        val description: String = ""
    )
}

sealed class ChoiceResponse {

    data class Answered(val answers: List<List<String>>) : ChoiceResponse()
    object Cancelled : ChoiceResponse()
}
