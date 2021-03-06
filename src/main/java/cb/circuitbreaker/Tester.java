package cb.circuitbreaker;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static cb.circuitbreaker.Printer.debug;
import static cb.circuitbreaker.Printer.print;

public class Tester {

	public static final String FAILURE = "fail";
	private static final int NUMBER_OF_TICKS = 80000;
	private static final int TICK_PERIOD = 1;
	private static final Random RANDOM = new Random();
	private static final int PERIOD = 20000;

	private final int parallelism;

	/**
	 * @param parallelism specifies how many request are processed concurrently by each circuit breaker under test.
	 */
	public Tester(int parallelism) {
		this.parallelism = parallelism;
	}

	/**
	 * Runs the test and shows the results.
	 *
	 * @param facades a list of {@link Facade} instances.
	 */
	void run(final List<Facade> facades) {
		print("Collecting statistics data. That takes %d seconds. Pleas wait.%nSome log lines may be displayed. You may ignore them.%n", NUMBER_OF_TICKS/1000);
		final var threadsPoolSize = this.parallelism * facades.size();
		// Build a graph that every millisecond generates a request, hits all the circuit breaker instances with said request, and accumulates all the responses for each circuit breaker instance.
		var graph = Flux.interval(Duration.ofMillis(TICK_PERIOD))
				.takeWhile(milli -> milli < NUMBER_OF_TICKS)
				.onBackpressureBuffer()
				.map(milli -> new Request(milli, isOk(milli)))
				.flatMap(request -> Flux.fromIterable(facades).map(facade -> new RequestAndFacade(request, facade)))
				.parallel(threadsPoolSize)
//				.runOn(Schedulers.newBoundedElastic(threadsPoolSize, 4, "myScheduler", 1, true))
				.runOn(Schedulers.newParallel("myScheduler", threadsPoolSize, true))
				.map(rah -> rah.facade.doSomething(rah.request))
				.sequential()
				.doOnNext(out -> debug("%d - %s - out=%s\n", out.request.milli, out.breakerName, out.response.toString()))
				.reduce(
						new TreeMap<String, Accum>(),
						(report, out) -> {
							var accum = report.get(out.breakerName);
							if (accum == null) {
								accum = new Accum();
								report.put(out.breakerName, accum);
							}
							var respondedSuccessfully = out.response.isPresent() && !out.response.get().equals(FAILURE);
							if (out.request.isOk && respondedSuccessfully) {
								accum.tryHits += 1;
							}
							if (out.request.isOk && !respondedSuccessfully) {
								accum.dropFails += 1;
							}
							if (!out.request.isOk && out.response.isEmpty()) {
								accum.dropHits += 1;
							}
							if (!out.request.isOk && out.response.isPresent()) {
								accum.tryFails += 1;
							}
							return report;
						}
				);

		var startNano = System.nanoTime();
		var statsByName = graph.toFuture().join();
		var testDuration = (System.nanoTime() - startNano) / 1_000_000;
		var report = statsByName.entrySet().stream()
				.map(e -> String.format("%20s: %s", e.getKey(), e.getValue()))
				.collect(Collectors.joining("\n"));

		var statsOfAlwaysClosed = statsByName.get("alwaysClosed");
		var sampleSuccesses = (statsOfAlwaysClosed.tryHits + statsOfAlwaysClosed.dropFails) * 100.0 / NUMBER_OF_TICKS;
		print("Report%nTest duration:%d%nSample successes: %5.2f%%%n", testDuration, sampleSuccesses);
		print("%20s%17s%17s%17s%17s%17s%17s%12s%n", "name", "hits", "fails", "tryHits", "tryFails", "dropHits", "dropFails", "closedTime");
		print("%s%n", report);
	}

	/**
	 * Accumulator of the statistics of a circuit breaker instance. One instance of this class is created for each circuit breaker instance under
	 * test.
	 */
	private static class Accum {
		/**
		 * number of service calls that were responded successfully. In other words, the CB made the right decision letting the call to continue.
		 */
		int tryHits;
		/**
		 * number of service calls that failed (not responded or responded with error). In other words, the CB made the wrong decision letting the call continue.
		 */
		int tryFails;
		/**
		 * number of request that were dropped when the service was unavailable. In other words, the CB made the right decision avoiding the call.
		 */
		int dropHits;
		/**
		 * number of request that were dropped (or the CB failed) when the service was available. In other words, the CB made the wrong decision avoiding the call.
		 */
		int dropFails;

		public String toString() {
			return String
					.format("%6d (%5.2f%%), %6d (%5.2f%%), %6d (%5.2f%%), %6d (%5.2f%%), %6d (%5.2f%%), %6d (%5.2f%%), %9.2f%%",
							tryHits + dropHits, (tryHits + dropHits) * 100.0 / NUMBER_OF_TICKS,
							tryFails + dropFails, (tryFails + dropFails) * 100.0 / NUMBER_OF_TICKS,
							tryHits, tryHits * 100.0 / (tryHits + tryFails),
							tryFails, tryFails * 100.0 / (tryHits + tryFails),
							dropHits, dropHits * 100.0 / (dropHits + dropFails),
							dropFails, dropFails * 100.0 / (dropHits + dropFails),
							(tryHits + tryFails) * 100.0 / NUMBER_OF_TICKS
					);
		}
	}

	/**
	 * Facade of an operation that calls a service through a circuit breakers. The user should create an instance of this class for each instance of
	 * circuit breaker he wants to include in the test.
	 */
	@FunctionalInterface
	interface Facade {
		Out doSomething(Request request);
	}

	@RequiredArgsConstructor
	private static class RequestAndFacade {
		final Request request;
		final Facade facade;
	}

	/**
	 * The request that is sent to the service. Note that the request already knows if the service will be able to respond it.
	 */
	@ToString
	@RequiredArgsConstructor
	static class Request {
		/**
		 * A request is generated every millisecond. This is the number of the millisecond since the test start.
		 */
		final long milli;
		/**
		 * Tells the service simulator if the call should succeed of fail.
		 */
		final boolean isOk;
	}

	/**
	 * A tuple that contains the response of the simulated service wrapped in an {@link Optional}, the request that originated said response, and the
	 * name that identifies the instance of circuit breaker that decorates the service call.
	 *
	 * The response {@link Optional} is empty when the service call is executed while the circuit breaker is in open state. Abrupt terminations of the
	 * service call should be handled and represented with a non-empty {@link Optional}.
	 */
	@ToString
	@RequiredArgsConstructor
	static class Out {
		final String breakerName;
		final Request request;
		final Optional<String> response;
	}

	/**
	 * Called during the {@link Request} generation to determines what the simulated service should respond: a successful or unsuccessful response.
	 * The probability of success depends on the received millisecond.
	 */
	private static boolean isOk(long milli) {
//		var ok = valleyPlateau(milli);
//		var ok = valleyClimb(milli);
//		var ok = climbPlateau(milli);
		var ok = milli < NUMBER_OF_TICKS / 2 ? valleyPlateau(milli) : climbPlateau(milli);
		debug("%d - isOk=%b\n", milli, ok);
		return ok;
	}


	/**
	 * ????????????
	 */
	private static boolean valleyPlateau(long milli) {
		return (milli / PERIOD) % 2 == 0;
	}

	/**
	 * /???/???
	 */
	private static boolean climbValley(long milli) {
		var millisSincePeriodStart = milli % PERIOD;
		final boolean ok;
		if ((milli / PERIOD) % 2 == 0) {
			ok = RANDOM.nextInt(PERIOD) < millisSincePeriodStart;
		} else {
			ok = false;
		}
		return ok;
	}

	/**
	 * ????????????
	 */
	private static boolean climbPlateau(long milli) {
		var millisSincePeriodStart = milli % PERIOD;
		final boolean ok;
		if ((milli / PERIOD) % 2 == 0) {
			ok = RANDOM.nextInt(PERIOD) < millisSincePeriodStart;
		} else {
			ok = true;
		}
		return ok;
	}


	/**
	 * Simulates a service method that takes some time to complete. This is the method that should be decorated by each circuit breaker under test.
	 *
	 * @return the received long converted to String after waiting some time.
	 */
	public String simulatedServiceMethod(long milli) {
		try {
			if (parallelism > 1) {
				Thread.sleep(parallelism);
			}
			return Long.toString(milli);
		} catch (InterruptedException e) {
			return String.format("work sleep interrupted at %s\n", milli);
		}
	}

}
