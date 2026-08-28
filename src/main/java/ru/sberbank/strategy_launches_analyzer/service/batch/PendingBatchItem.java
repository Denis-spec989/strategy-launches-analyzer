package ru.sberbank.strategy_launches_analyzer.service.batch;

import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;

import java.util.concurrent.Future;

record PendingBatchItem(Future<BatchItemResult> future) {
}
