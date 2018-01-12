package actors;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.node.ObjectNode;

import actors.BookingMessages.ConfirmRequest;
import actors.BookingMessages.DebugRequest;
import actors.BookingMessages.HoldRequest;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import controllers.HomeController;
import controllers.HomeController.Response;
import play.libs.Json;
import scala.concurrent.duration.Duration;
import services.DataService;

public class ActorAA extends AbstractActor {

	private DataService ds;

	@Inject
	public ActorAA(DataService ds) {
		this.ds = ds;
	}

	public static Map<Long, String> bookingToken = Collections.synchronizedMap(new LinkedHashMap<Long, String>());

	public static Set<String> confirmReplyFail = Collections.synchronizedSet(new HashSet<String>());

	public static Set<String> confirmNoResponse = Collections.synchronizedSet(new HashSet<String>());

	public static Props getProps() {
		return Props.create(ActorAA.class);
	}

	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(HoldRequest.class, request -> {
			ObjectNode obj = Json.newObject();

			Integer totalFlightCapacity = ds.getTotalFlightCapacity(request.flightNo);

			Integer occupiedSeats = ds.getCurrentFlightCapacity(request.flightNo);

			if (totalFlightCapacity == null || occupiedSeats == null) {
				sendErrorResponse("Database error", request.transactionId, request.flightNo);
			}

			int queue = 0;

			for (String flightNo : bookingToken.values()) {
				if (flightNo.equals(request.flightNo)) {
					++queue;
				}
			}

			if (totalFlightCapacity > (occupiedSeats + queue)) {

				bookingToken.put(request.transactionId, request.flightNo);
				obj.put(HomeController.STATUS, HomeController.SUCCESS);
				obj.put(HomeController.MESSAGE, "Your hold will expire in 5 mins");

				Response res = new Response(obj);

				sender().tell(res, self());

				getContext().getSystem().scheduler().scheduleOnce(Duration.create(6, TimeUnit.MINUTES), new Runnable() {
					@Override
					public void run() {
						if (bookingToken.containsKey(request.transactionId)) {
							bookingToken.remove(request.transactionId);
							LOG.info("Removing transaction" + request.transactionId + " due to inactivity!");

						}
					}
				}, getContext().getSystem().dispatcher());

			} else {
				sendErrorResponse("All the seats are booked", request.transactionId, request.flightNo);
			}

		}).match(ConfirmRequest.class, request -> {

			String airline = request.flightNo.substring(0, 2);
			ObjectNode obj = Json.newObject();

			if (confirmReplyFail.contains(airline)) {

			} else if (confirmNoResponse.contains(airline)) {

				sendErrorResponse("No booking till further notice", request.transactionId, request.flightNo);

			} else {

				if (bookingToken.containsKey(request.transactionId)) {
					// bookingToken.remove(request.transactionId);
					obj.put(HomeController.STATUS, HomeController.SUCCESS);
					obj.put(HomeController.MESSAGE, "You can book the flight.");
					Response res = new Response(obj);

					sender().tell(res, self());

				} else {
					sendErrorResponse("Booking time elapsed!", request.transactionId, request.flightNo);
				}
			}
		}).match(DebugRequest.class, request -> {
			if (request.confirmNoResponse != null) {
				confirmNoResponse.add(request.airline);
			} else if (request.confirmFail != null) {
				confirmReplyFail.add(request.airline);
			} else {
				confirmNoResponse.remove(request.airline);
				confirmReplyFail.remove(request.airline);
			}

			ObjectNode obj = Json.newObject();
			obj.put(HomeController.STATUS, HomeController.SUCCESS);
			Response res = new Response(obj);

			sender().tell(res, self());

		}).build();
	}

	private void sendErrorResponse(String message, Long transactionId, String flightNo) {
		ObjectNode obj = Json.newObject();
		obj.put(HomeController.TRANSACTION_ID, transactionId);

		obj.put(HomeController.FLIGHT_NO, flightNo);

		obj.put(HomeController.MESSAGE, message);

		obj.put(HomeController.STATUS, HomeController.ERROR);

		Response res = new Response(obj);

		sender().tell(res, self());
	}
}
