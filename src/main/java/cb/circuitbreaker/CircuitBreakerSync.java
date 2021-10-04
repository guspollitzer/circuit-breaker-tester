package cb.circuitbreaker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CircuitBreakerSync extends CircuitBreaker {

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
	public CircuitBreakerSync(
			final double breakThreshold,
			final int initialRecoverMillis,
			final double alfa,
			final Chrono chrono
	) {
		super(breakThreshold, initialRecoverMillis, alfa, chrono);
	}

	/**
	 * Invokes the specified `supplier` trough this circuit breaker.
	 *
	 * The `supplier` is not invoked if this instance is open.
	 *
	 * The circuit is opened when the exponential moving average of the proportion of failures ({@code failures/(failures + successes)}) crosses the
	 * `breakThreshold`.
	 *
	 * Both; abrupt terminations of either, the `supplier` of the `isOkDecider`, and results such that applying the `isOkDecider` predicate to it give
	 * false; are considered failures.
	 */
	public <T> Optional<T> apply(
			final Supplier<T> supplier,
			final Predicate<T> isOkDecider,
			final StateChangeListener listener
	) {
		final var now = chrono.nanoTime();
		if (isBroken) {
			synchronized (this) {
				if (isBroken && now < nextTryNano) {
					return Optional.empty();
				}
			}
		}
		try {
			final T result = supplier.get();
			updateRevealingly(now, !isOkDecider.test(result), listener);
			return Optional.ofNullable(result);
		} catch (Exception e) {
			updateRevealingly(now, true, listener);
			throw e;
		}
	}

	public <T> CompletableFuture<Optional<T>> applyAsync(
			final Supplier<CompletableFuture<T>> supplier,
			final Predicate<T> isOkDecider,
			final StateChangeListener listener
	) {
		final var now = chrono.nanoTime();
		if (isBroken) {
			synchronized (this) {
				if (isBroken && now < nextTryNano) {
					return CompletableFuture.completedFuture(Optional.empty());
				}
			}
		}
		try {
			return supplier.get().thenApply(
					result -> {
						updateRevealingly(now, !isOkDecider.test(result), listener);
						return Optional.ofNullable(result);
					}
			);
		} catch (Exception e) {
			updateRevealingly(now, true, listener);
			throw e;
		}
	}

	/**
	 * Updates the state of this instance and informs the listener of any change.
	 */
	private void updateRevealingly(
			final long now,
			final boolean hasFailed,
			final StateChangeListener listener
	) {

		final boolean brokenStateChanged;
		final boolean failuresProportionChanged;
		final boolean triesChanged;
		boolean copyOfBrokenState;
		double copyOfFailuresProportion;
		int copyOfTries;

		synchronized (this) {
			copyOfBrokenState = isBroken;
			copyOfFailuresProportion = failuresProportionEma;
			copyOfTries = tries;

			update(now, hasFailed);

			brokenStateChanged = isBroken != copyOfBrokenState;
			failuresProportionChanged =
					Math.abs(failuresProportionEma - copyOfFailuresProportion) > ONE_PERCENT;
			triesChanged = tries != copyOfTries;

			copyOfBrokenState = isBroken;
			copyOfFailuresProportion = failuresProportionEma;
			copyOfTries = tries;
		}
		if (brokenStateChanged) {
			listener.brokenStateChanged(copyOfBrokenState);
		}
		if (failuresProportionChanged) {
			listener.failuresProportionChanged(copyOfFailuresProportion);
		}
		if (triesChanged) {
			listener.triesChanged(copyOfTries);
		}
	}
}
