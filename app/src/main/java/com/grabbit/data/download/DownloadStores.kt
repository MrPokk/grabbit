package com.grabbit.data.download

import com.grabbit.data.db.HistoryDao
import com.grabbit.data.ytdlp.InfoCacheStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadStores
    @Inject
    constructor(
        val historyDao: HistoryDao,
        val infoCache: InfoCacheStore,
    )
