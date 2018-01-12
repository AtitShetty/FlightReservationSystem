package controllers;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.databind.node.ObjectNode;

import actors.ActorAA;
import actors.BookingMessages;
import actors.BookingMessages.DebugRequest;
import actors.BookingMessages.OperatorQuery;
import actors.BookingMessages.ReservationQuery;
import actors.BookingMessages.TripQuery;
import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.Duration;
import services.DataService;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
	
	Logger.ALogger LOG = Logger.of(HomeController.class);

	final ActorRef actorBooking;

	final ActorRef actorAA;

	public static final String STATUS = "status";

	public static final String SUCCESS = "success";

	public static final String ERROR = "error";

	public static final String TRIPS = "trips";

	public static final String SEGMEMTS = "segments";

	public static final String MESSAGE = "message";

	public static final String BLANK = "";

	public static final String OPERATORS = "operators";

	public static final String FLIGHTS = "flights";

	public static final String SEATS = "seats";

	public static final String TRANSACTION_ID = "transaction_id";

	public static final String FLIGHT_NO = "flight_no";

	public static final String TRIP_ID = "tripID";

	public final Random randomizer = new Random();

	private DataService ds;

	@Inject
	public HomeController(@Named("actorBooking") ActorRef actorBooking, @Named("actorAA") ActorRef actorAA,
			DataService ds) {
		this.actorBooking = actorBooking;
		this.actorAA = actorAA;
		this.ds = ds;
	}

	public Result index() throws InterruptedException, ExecutionException {
		
		String response = "<h1>Welcome to MyFlight</h1>";

		return ok(response).as("text/html");
	}
	
	public Result getTrips() {
		return getSegments(HomeController.BLANK);
	}

	public Result getSegments(String tripId) {
		
		TripQuery query = new TripQuery(tripId);

		return getResponse(query, 2, actorBooking);

	}

	public Result getOperators() {
		return getAvailableSeats(null, null);
	}

	public Result getFlights(String operator) {
		return getAvailableSeats(operator, null);
	}

	public Result getAvailableSeats(String operator, String flight) {

		OperatorQuery query = new OperatorQuery(operator, flight);

		return getResponse(query, 2, actorBooking);
	}

	public Result reserveSeats(String from, String to) {

		ReservationQuery query = new ReservationQuery(from, to, randomizer.nextLong());

		return getResponse(query, 10, actorBooking);

	}

	public Result setDebugFlagConfirmFail(String airline) {
		DebugRequest query = new DebugRequest(airline, true, null, null);

		return getResponse(query, 2, actorAA);

	}

	public Result setDebugFlagConfirmNoResponse(String airline) {
		DebugRequest query = new DebugRequest(airline, null, true, null);

		return getResponse(query, 2, actorAA);

	}

	public Result resetDebugFlags(String airline) {
		DebugRequest query = new DebugRequest(airline, null, null, true);

		return getResponse(query, 2, actorAA);

	}

	public Result getResponse(BookingMessages query, int duration, ActorRef actor) {

		Timeout timeout = new Timeout(Duration.create(duration, TimeUnit.MINUTES));

		try {

			Response queryResponse = (Response) FutureConverters.toJava(Patterns.ask(actor, query, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();

			return ok(queryResponse.response);

		} catch (Exception e) {
			LOG.error("Exception while processing query from " + query.getClass().getSimpleName(), e);
			ObjectNode obj = Json.newObject();
			obj.put(HomeController.STATUS, HomeController.ERROR);
			obj.put(HomeController.MESSAGE, e.getMessage());

			return ok(obj);
		}

	}

	public Result resetEverything() {

		ActorAA.bookingToken.clear();

		ActorAA.confirmNoResponse.clear();

		ActorAA.confirmReplyFail.clear();

		ds.clearTransactions();

		return ok("Done");

	}


	public static class Response {
		public final ObjectNode response;

		public Response(ObjectNode response) {
			this.response = response;
		}
	}


}
