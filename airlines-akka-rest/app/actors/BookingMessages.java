package actors;

public interface BookingMessages {

	public static class HoldRequest implements BookingMessages {

		public final long transactionId;

		public final String flightNo;

		public HoldRequest(String flightNo, long transactionId) {

			this.transactionId = transactionId;

			this.flightNo = flightNo;
		}

	}

	public static class ConfirmRequest implements BookingMessages {

		public final long transactionId;

		public final String flightNo;

		public ConfirmRequest(String flightNo, long transactionId) {

			this.transactionId = transactionId;

			this.flightNo = flightNo;
		}

	}

	public static class DebugRequest implements BookingMessages {
		public final String airline;
		public final Boolean confirmFail;
		public final Boolean confirmNoResponse;
		public final Boolean reset;

		public DebugRequest(String airline, Boolean confirmFail, Boolean confirmNoResponse, Boolean reset) {
			this.confirmFail = confirmFail;
			this.confirmNoResponse = confirmNoResponse;
			this.reset = reset;
			this.airline = airline;
		}

	}

	public static class TripQuery implements BookingMessages {
		public final String tripId;

		public TripQuery(String tripId) {
			this.tripId = tripId;
		}
	}

	public static class OperatorQuery implements BookingMessages {
		public final String operator;

		public final String flight;

		public OperatorQuery(String operator, String flight) {
			this.operator = operator;
			this.flight = flight;
		}
	}

	public static class ReservationQuery implements BookingMessages {

		public final long transactionId;

		public final String to;

		public final String from;

		public ReservationQuery(String from, String to, long transactionId) {
			this.to = to;
			this.from = from;
			this.transactionId = transactionId;
		}
	}
}
