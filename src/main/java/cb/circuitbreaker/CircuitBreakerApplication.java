package cb.circuitbreaker;

import com.mercadolibre.resilience.breaker.Action;
import com.mercadolibre.resilience.breaker.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CircuitBreakerApplication {

	private static final int NUMBER_OF_TICKS = 80000;
	private static final int TICK_PERIOD = 1;
	private static final Random RANDOM = new Random();
	private static final int PERIOD = 20000;
	private static final int PARALLELISM = 20;

	final List<Handler> handlers;

	@SneakyThrows
	public static void main(String[] args) {
		new CircuitBreakerApplication().run();
	}

	CircuitBreakerApplication() {

		// Create the instances of CircuitBreakerSync that will be tested and compared, and wrap them within a Handler to homogenize the usage.
		final Stream<Handler> myHandlers;
		{
			var breaker1 = new CircuitBreakerSync(0.5, 32, 0.02, System::nanoTime);
			var breaker2 = new CircuitBreakerSync(0.5, 64, 0.02, System::nanoTime);
			var breaker3 = new CircuitBreakerSync(0.5, 128, 0.02, System::nanoTime);
			var breaker4 = new CircuitBreakerSync(0.5, 32, 0.05, System::nanoTime);
			var breaker5 = new CircuitBreakerSync(0.5, 64, 0.05, System::nanoTime);
			var breaker6 = new CircuitBreakerSync(0.5, 128, 0.05, System::nanoTime);
			var myBreakers = Map.of(
					"myBreaker1", breaker1,
					"myBreaker2", breaker2,
					"myBreaker3", breaker3,
					"myBreaker4", breaker4,
					"myBreaker5", breaker5,
					"myBreaker6", breaker6
			);
			myHandlers = myBreakers.entrySet().stream()
					.map(entry -> buildCircuitBreakerSyncHandler(entry.getKey(), entry.getValue()));
		}

		// Create the instances of MeLi circuit breaker that will be tested and compared, and wrap them within a Handler to homogenize the usage.
		final Stream<Handler> libHandlers;
		{
			var meliBreaker1 = CircuitBreakers.newExponentialBreaker("1", 100, 60, 0.2, 2, 60, 0.5, 1, 2);
			var meliBreaker2 = CircuitBreakers.newExponentialBreaker("2", 100, 120, 0.2, 4, 60, 0.5, 1, 2);
			var meliBreaker3 = CircuitBreakers.newExponentialBreaker("3", 100, 240, 0.2, 8, 60, 0.5, 1, 2);
			var meliBreaker4 = CircuitBreakers.newExponentialBreaker("4", 10, 60, 0.2, 2, 60, 0.5, 1, 2);
			var meliBreaker5 = CircuitBreakers.newExponentialBreaker("5", 10, 120, 0.2, 4, 60, 0.5, 1, 2);
			var meliBreaker6 = CircuitBreakers.newExponentialBreaker("6", 10, 240, 0.2, 8, 60, 0.5, 1, 2);
			var meliBreakers = Map.of(
					"MeLi Breaker1", meliBreaker1,
					"MeLi Breaker2", meliBreaker2,
					"MeLi Breaker3", meliBreaker3,
					"MeLi Breaker4", meliBreaker4,
					"MeLi Breaker5", meliBreaker5,
					"MeLi Breaker6", meliBreaker6
			);
			libHandlers = meliBreakers.entrySet().stream()
					.map(entry -> buildMeliCircuitBreakerHandler(entry.getKey(), entry.getValue()));
		}

		// Create the instances of resilence 4j circuit breaker that will be tested and compared, and wrap them within a Handler to homogenize the usage.
		final Stream<Handler> res4jHandlers;
		{
			var resConfig1 = new CircuitBreakerConfig.Builder()
					.slidingWindow(4, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig2 = new CircuitBreakerConfig.Builder()
					.slidingWindow(8, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig3 = new CircuitBreakerConfig.Builder()
					.slidingWindow(16, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig4 = new CircuitBreakerConfig.Builder()
					.slidingWindow(32, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig5 = new CircuitBreakerConfig.Builder()
					.slidingWindow(4, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig6 = new CircuitBreakerConfig.Builder()
					.slidingWindow(8, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig7 = new CircuitBreakerConfig.Builder()
					.slidingWindow(16, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfig8 = new CircuitBreakerConfig.Builder()
					.slidingWindow(32, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult(out -> "fail".equals(out))
					.build();
			var resConfigs = Map.of(
					"res4j config1", resConfig1,
					"res4j config2", resConfig2,
					"res4j config3", resConfig3,
					"res4j config4", resConfig4,
					"res4j config5", resConfig5,
					"res4j config6", resConfig6,
					"res4j config7", resConfig7,
					"res4j config8", resConfig8
			);
			res4jHandlers = resConfigs.entrySet().stream()
					.map(entry -> buildRes4jCircuitBreakerHandler(entry.getKey(), entry.getValue()));
		}

		// Create a handler with no circuit breaker (is always closed).
		var alwaysClosed = new Handler() {
			@Override
			public Out handle(Request request) {
				return new Out("alwaysClosed", request, Optional.of(Long.toString(request.milli)));
			}
		};

		// initialize the list that contains all the circuit breakers under test.
		handlers = Stream.concat(
				Stream.concat(Stream.of(alwaysClosed), myHandlers),
				Stream.concat(libHandlers, res4jHandlers)
		).collect(Collectors.toList());
	}

	/** Runs the test and shows the results */
	void run() {
		var graph = Flux.interval(Duration.ofMillis(TICK_PERIOD))
				.takeWhile(milli -> milli < NUMBER_OF_TICKS)
				.onBackpressureBuffer()
				.map(milli -> new Request(milli, isOk(milli)))
				.flatMap(request -> Flux.fromIterable(handlers).map(handler -> new RequestAndHandler(request, handler)))
				.parallel(PARALLELISM)
//				.runOn(Schedulers.newBoundedElastic(PARALLELISM, 4, "myScheduler", 1, true))
				.runOn(Schedulers.newParallel("myScheduler", 8, true))
				.map(rah -> rah.handler.handle(rah.request))
				.sequential()
				.doOnNext(out -> debug("%d - %s - out=%s\n", out.request.milli, out.handlerName, out.response.toString()))
				.reduce(
						new TreeMap<String, Accum>(),
						(report, out) -> {
							var accum = report.get(out.handlerName);
							if (accum == null) {
								accum = new Accum();
								report.put(out.handlerName, accum);
							}
							if (out.request.isOk && out.response.isPresent()) {
								accum.tryHits += 1;
							}
							if (out.request.isOk && out.response.isEmpty()) {
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
		print("Report\nTest duration:%d\nSample successes: %5.2f%%\n%s", testDuration, sampleSuccesses, report);
		printer.shutdown();
	}

	/** Builds a {@link Handler} for my custom synchronous version of circuit breaker */
	Handler buildCircuitBreakerSyncHandler(String name, CircuitBreakerSync breaker) {
		return request -> {
			var response = breaker.execute(
					() -> work(request.milli),
					r -> request.isOk,
					new CircuitBreaker.StateChangeListener() {
						@Override
						public void brokenStateChanged(boolean isBroken) {
							debug("%d - %s - open=%b\n", request.milli, name, isBroken);
						}

						@Override
						public void failuresProportionChanged(double newValue) {
							debug("%d - %s - failProp=%f\n", request.milli, name, newValue);
						}

						@Override
						public void triesChanged(int newValue) {
							debug("%d - %s - tries=%d\n", request.milli, name, newValue);

						}
					}
			);
			return new Out(name, request, response);
		};
	}

	/** Builds a {@link Handler} for a MeLi circuit breaker */
	Handler buildMeliCircuitBreakerHandler(String name, com.mercadolibre.resilience.breaker.CircuitBreaker cb) {
		return request -> {
			Optional<String> oResult;
			try {
				var result = cb.run(new Action<String>() {
					@Override
					public boolean isValid(String result, Throwable t) {
						return request.isOk;
					}

					@Override
					public String get() throws Exception {
						return work(request.milli);
					}
				});
				oResult = Optional.of(result);
			} catch (RejectedExecutionException e) {
				oResult = Optional.empty();
			} catch (ExecutionException e) {
				e.printStackTrace();
				oResult = Optional.empty();
			}
			return new Out(name, request, oResult);
		};
	}

	/** Builds a {@link Handler} for a resilience 4j circuit breaker */
	private Handler buildRes4jCircuitBreakerHandler(String name, CircuitBreakerConfig cbc) {
		final var cb = io.github.resilience4j.circuitbreaker.CircuitBreaker.of(name, cbc);
		final Function<Long, String> decoratedWork = milli -> cb.executeSupplier(() -> work(milli));
		return request -> {
			Optional<String> oResult;
//			print("state of %s before is: %s\n\t; metrics: failuresRate=%f, failures=%d\n", name, cb.getState(), cb.getMetrics().getFailureRate(), cb.getMetrics().getNumberOfFailedCalls());
			try {
				var result = cb.executeSupplier(() -> {
					var r = work(request.milli);
					return request.isOk ? r : "fail";
				});
				oResult = Optional.of(result);
			} catch (CallNotPermittedException e) {
				oResult = Optional.empty();
			} catch (Exception e) {
				e.printStackTrace();
				oResult = Optional.empty();
//			} finally {
//				print("state of %s after is: %s\n\t; metrics: failuresRate=%f, failures=%d\n", name, cb.getState(), cb.getMetrics().getFailureRate(), cb.getMetrics().getNumberOfFailedCalls());
			}
			return new Out(name, request, oResult);
		};
	}

	/** Accumulator of the statistics of a breaker instance. */
	static class Accum {
		/**
		 * number of service calls that were responded successfully
		 */
		int tryHits;
		/**
		 * number of service calls that failed (not responded or responded with error)
		 */
		int tryFails;
		/**
		 * number of request that were dropped when the service was unavailable
		 */
		int dropHits;
		/**
		 * number of request that were dropped when the service was available
		 */
		int dropFails;

		public String toString() {
			return String
					.format("hits=%6d (%5.2f%%), fails=%6d (%5.2f%%), tryHits=%6d (%5.2f%%), tryFails=%6d (%5.2f%%), dropHits=%6d (%5.2f%%), dropFails=%6d (%5.2f%%), closedTime=%6.2f%%",
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

	/** A function that takes a {@link Request} and gives an {@link Out}.
	 * Used to homogenize the usage of different kinds of circuit breakers.
	 * Each instance of this class owns an instance of circuit breaker which is used to "protect" the service. */
	@FunctionalInterface
	interface Handler {
		Out handle(Request request);
	}

	@RequiredArgsConstructor
	static class RequestAndHandler {
		final Request request;
		final Handler handler;
	}

	/** The request that is sent to the service.
	 * Note that the request already knows if the service will be able to respond it. */
	@ToString
	@RequiredArgsConstructor
	static class Request {
		final long milli;
		final boolean isOk;
	}

	@ToString
	@RequiredArgsConstructor
	class Out {
		final String handlerName;
		final Request request;
		final Optional<String> response;
	}

	/** Simulates de work that the decorated service does.
	 * @return the received long converted to String after waiting some time. */
	String work(long milli) {
		try {
			//
			Thread.sleep(Math.max(0, TICK_PERIOD * PARALLELISM / handlers.size() - 2));
			return Long.toString(milli);
		} catch (InterruptedException e) {
			return String.format("work sleep interrupted at %s\n", milli);
		}
	}


	static boolean isOk(long milli) {
//		var ok = plateauValley(milli);
//		var ok = valleyClimb(milli);
//		var ok = plateauClimb(milli);
		var ok = milli < NUMBER_OF_TICKS / 2 ? plateauValley(milli) : plateauClimb(milli);
		debug("%d - isOk=%b\n", milli, ok);
		return ok;
	}


	/**
	 * ⎴⎵⎴⎵
	 */
	static boolean plateauValley(long milli) {
		return (milli / PERIOD) % 2 == 0;
	}

	/**
	 * ⎵/⎵/
	 */
	static boolean valleyClimb(long milli) {
		var millisSincePeriodStart = milli % PERIOD;
		final boolean ok;
		if ((milli / PERIOD) % 2 == 1) {
			ok = RANDOM.nextInt(PERIOD) < millisSincePeriodStart;
		} else {
			ok = false;
		}
		return ok;
	}

	/**
	 * ⎴╱⎴╱
	 */
	static boolean plateauClimb(long milli) {
		var millisSincePeriodStart = milli % PERIOD;
		final boolean ok;
		if ((milli / PERIOD) % 2 == 1) {
			ok = RANDOM.nextInt(PERIOD) < millisSincePeriodStart;
		} else {
			ok = true;
		}
		return ok;
	}


	////

	static ExecutorService printer = Executors.newSingleThreadExecutor();

	static void print(String format, Object... args) {
		printer.execute(() -> System.out.printf(format, args));
	}

	static void debug(String format, Object... args) {
//		printer.execute(() -> System.out.printf(format, args));
	}

}
