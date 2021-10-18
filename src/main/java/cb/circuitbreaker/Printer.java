package cb.circuitbreaker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Printer {
	private static ExecutorService printer = Executors.newSingleThreadExecutor();

	static void shutdown() {
		printer.shutdown();
	}

	static void print(String format, Object... args) {
		printer.execute(() -> System.out.printf(format, args));
	}

	static void debug(String format, Object... args) {
//		printer.execute(() -> System.out.printf(format, args));
	}



}
