package cb.circuitbreaker;

import java.util.Random;

public abstract class CircuitBreaker {

	protected static final double ONE_PERCENT = 0.01;
	protected static final long NANOS_PER_MILLI = 1_000_000;
	private static final Random RANDOM = new Random();
	/**
	 * The threshold that determines when to open this circuit breaker. When the exponential moving average of the proportion of failures is over this
	 * value, the circuit is opened.
	 */
	protected final double breakThreshold;
	/**
	 * The amount of time to wait after the first emission failure to do a retry. This time is double for each retry
	 */
	protected final long initialRecoverNanos;
	/**
	 * The exponential moving average coefficient.
	 */
	protected final double alfa;

	/**
	 * The chronometer used to measure elapsed time.
	 */
	protected final Chrono chrono;
	/**
	 * The state of this circuit breaker switch: false -> closed; true -> open or half-open depending on if {@code chrono.nanoTime() < nextTryNano} is
	 * false or true respectively.
	 */
	protected volatile boolean isBroken;
	/**
	 * The instant until which the circuit will remain open, before switching to half-open.
	 */
	protected long nextTryNano;
	/**
	 * Current value of the exponential moving average of the proportion of failures.
	 */
	protected double failuresProportionEma;
	/**
	 * The number of consecutive unsuccessful tries after the circuit was opened. After an unsuccessful try in the half-open state, the circuit is
	 * opened and remains open during {@code initialRecoverMillis * 2^tries}.
	 */
	protected int tries;

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
	protected CircuitBreaker(
			final double breakThreshold,
			final int initialRecoverMillis,
			final double alfa,
			final Chrono chrono
	) {
		this.breakThreshold = breakThreshold;
		this.initialRecoverNanos = initialRecoverMillis * NANOS_PER_MILLI;
		this.alfa = alfa;
		this.chrono = chrono;
	}

	/**
	 * Updates the state of this instance. This method does not support concurrency.
	 */
	protected void update(final long now, final boolean hasFailed) {
		if (hasFailed && isBroken) {
			if (now >= nextTryNano) {
				// reaches here if the try was unsuccessful and the circuit is half open
				tries += 1;
				// apply a -20% to +25% randomness to the retry delay
				var retryDelayRandomnessX100 = (RANDOM.nextInt(45) + 80);
				nextTryNano = now + (initialRecoverNanos * (1L << tries) * retryDelayRandomnessX100) / 100;
			}
		} else {
			// reaches here if the try was successful and the circuit is closed
			failuresProportionEma = failuresProportionEma * (1d - alfa) + (hasFailed ? alfa : 0d);
			if (hasFailed) {
				if (failuresProportionEma > breakThreshold) {
					isBroken = true;
					nextTryNano = now + initialRecoverNanos;
				}
			} else {
				isBroken = false;
				tries = 0;
			}
		}
	}

	public interface StateChangeListener {
		void brokenStateChanged(boolean isBroken);

		void failuresProportionChanged(double newValue);

		void triesChanged(int newValue);
	}

	@FunctionalInterface
	public interface Chrono {
		long nanoTime();
	}

}
