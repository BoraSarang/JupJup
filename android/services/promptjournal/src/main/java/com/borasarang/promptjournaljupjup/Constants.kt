package com.borasarang.promptjournaljupjup

object Constants {
    const val DEFAULT_PORT = 3030
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    const val DB_NAME = "promptjournal.db"
    const val DATASTORE_NAME = "pj_settings"

    /** 에러코드 프리픽스 */
    const val ERR_PREFIX = "E-AND-REPORT"

    // 에러코드 상수
    const val ERR_AI_CALL_FAILED = "$ERR_PREFIX-0801"
    const val ERR_DB_INIT_FAILED = "$ERR_PREFIX-0802"
    const val ERR_SETTINGS_SAVE_FAILED = "$ERR_PREFIX-0803"
    const val ERR_SCHEDULE_FAILED = "$ERR_PREFIX-0804"
    const val ERR_NOTIFICATION_FAILED = "$ERR_PREFIX-0805"
}
