package cb.circuitbreaker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;


public class CircuitBreakerExec extends CircuitBreaker {

	/**
	 * The single thread executor used to update this instance state.
	 */
	private final Executor singleThreadExecutor;

	/**
	 * Construct an instance specifying all the parameters.
	 *
	 * @param breakThreshold       the threshold that determines when to open this circuit breaker. When the exponential moving average of the
	 *                             proportion of failures is over this value, the circuit is opened.
	 * @param initialRecoverMillis the amount of time that the circuit remains opened before switching to half-open state. This period is doubled
	 *                             every consecutive failed attempt.
	 * @param alfa                 the coefficient of the exponential moving average of the proportion of failures.
	 * @param chrono               the chronometer used to measure the elapsed time.
	 */
	public CircuitBreakerExec(
			final double breakThreshold,
			final int initialRecoverMillis,
			final double alfa,
			final Chrono chrono,
			final ExecutorService aSingleThreadExecutor
	) {
		super(breakThreshold, initialRecoverMillis, alfa, chrono);
		this.singleThreadExecutor = aSingleThreadExecutor;
	}

	public <T> CompletableFuture<Optional<T>> applyAsync(
			final Supplier<CompletableFuture<T>> supplier,
			final Predicate<T> isOkDecider,
			final StateChangeListener listener
	) {
		final var now = chrono.nanoTime();
		return CompletableFuture
				.supplyAsync(
						() -> isBroken && now < nextTryNano,
						singleThreadExecutor
				)
				.thenCompose(isOpen -> {
							if (isOpen) {
								return CompletableFuture.completedFuture(Optional.empty());
							}
							try {
								return supplier.get().thenApplyAsync(
										result -> {
											updateRevealingly(now, !isOkDecider.test(result), listener);
											return Optional.ofNullable(result);
										},
										singleThreadExecutor
								);
							} catch (Exception e) {
								updateRevealingly(now, true, listener);
								return CompletableFuture.failedFuture(e);
							}
						}
				);
	}

	/**
	 * Updates the state of this instance and informs the listener of any change.
	 */
	private void updateRevealingly(
			final long now,
			final boolean hasFailed,
			final StateChangeListener listener
	) {
		var copyOfBrokenState = isBroken;
		var copyOfFailuresProportion = failuresProportionEma;
		var copyOfTries = tries;

		update(now, hasFailed);

		if (isBroken != copyOfBrokenState) {
			listener.brokenStateChanged(isBroken);
		}
		if (Math.abs(failuresProportionEma - copyOfFailuresProportion) > ONE_PERCENT) {
			listener.failuresProportionChanged(failuresProportionEma);
		}
		if (tries != copyOfTries) {
			listener.triesChanged(tries);
		}
	}
}
