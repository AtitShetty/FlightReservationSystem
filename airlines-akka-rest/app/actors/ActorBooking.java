package actors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.databind.node.ObjectNode;

import actors.BookingMessages.ConfirmRequest;
import actors.BookingMessages.HoldRequest;
import actors.BookingMessages.OperatorQuery;
import actors.BookingMessages.ReservationQuery;
import actors.BookingMessages.TripQuery;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;
import controllers.HomeController;
import controllers.HomeController.Response;
import play.libs.Json;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.Duration;
import services.DataService;

public class ActorBooking extends AbstractActor {

	private DataService dataService;

	private ActorRef actorAA;

	@Inject
	public ActorBooking(DataService ds, @Named("actorAA") ActorRef actor) {
		this.dataService = ds;
		this.actorAA = actor;
	}

	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	private static final String HOLD = "hold";

	private static final String CONFIRM = "confirm";

	private static final String STATE = "state";

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(TripQuery.class, request -> {
			executeTripQueryRequest(request);
		}).match(OperatorQuery.class, request -> {
			executeOperatorQuery(request);
		}).match(ReservationQuery.class, request -> {
			executeReservationQuery(request);
		}).build();
	}

	private void executeTripQueryRequest(TripQuery request) {

		LOG.debug("Inside TripQuery");

		Response res;

		List<String> result = dataService.getTripDetails(request.tripId);

		ObjectNode obj = Json.newObject();

		if (result != null) {
			LOG.info("TripQuery success");

			if (request.tripId.equals("")) {
				obj.set(HomeController.TRIPS, Json.toJson(result));
			} else {
				obj.set(HomeController.SEGMEMTS, Json.toJson(result));
			}

			obj.put(HomeController.STATUS, HomeController.SUCCESS);

			res = new Response(obj);

			sender().tell(res, self());
		} else {
			LOG.info("TripQuery error");

			sendErrorResponse("Error connecting to database");
		}

	}

	private void executeOperatorQuery(OperatorQuery query) {
		Response res;

		ObjectNode obj = Json.newObject();

		Collection<String> result;

		List<String> paramValues;

		if (query.operator != null && query.flight != null) {

			paramValues = new ArrayList<>(2);

			paramValues.add(query.operator);

			paramValues.add(query.flight);

			result = dataService.getOperatorDetails("CAPACITY", "CODE=? AND FLIGHT_NO=? ", paramValues);

			if (result != null) {

				if (result.size() > 0) {
					obj.set(HomeController.SEATS, Json.toJson(result));
					obj.put(HomeController.STATUS, HomeController.SUCCESS);

					res = new Response(obj);

					sender().tell(res, self());
				} else {
					sendErrorResponse("Flight details not available. Please check the operator and flight number");
				}
			} else {
				sendErrorResponse("Error connecting to database");
			}

		} else if (query.operator != null) {
			paramValues = new ArrayList<>(1);

			paramValues.add(query.operator);

			result = dataService.getOperatorDetails("FLIGHT_NO", "CODE=? ", paramValues);

			if (result != null) {

				if (result.size() > 0) {
					obj.set(HomeController.FLIGHTS, Json.toJson(result));
					obj.put(HomeController.STATUS, HomeController.SUCCESS);

					res = new Response(obj);

					sender().tell(res, self());
				} else {
					sendErrorResponse("Flight details not available. Please check the operator");
				}
			} else {
				sendErrorResponse("Error connecting to database");
			}
		} else {

			paramValues = null;

			result = dataService.getOperatorDetails("CODE", null, null);

			if (result != null) {

				if (result.size() > 0) {
					obj.set(HomeController.OPERATORS, Json.toJson(result));
					obj.put(HomeController.STATUS, HomeController.SUCCESS);

					res = new Response(obj);

					sender().tell(res, self());
				} else {
					sendErrorResponse("No operators found");
				}
			} else {
				sendErrorResponse("Error connecting to database");
			}

		}
	}

	private void executeReservationQuery(ReservationQuery query) {
		if (!query.from.equals("X") || !query.to.equals("Y")) {
			sendErrorResponse("Please enter valid origin/destination");
		} else {
			if (isPath1Available(query) || isPath2Available(query) || isPath3Available(query)) {
				ObjectNode obj = Json.newObject();
				obj.put(HomeController.STATUS, HomeController.SUCCESS);
				obj.put(HomeController.TRIP_ID, query.transactionId);
				
				Response res = new Response(obj);
				
				sender().tell(res, self());
			} else {
				sendErrorResponse("Cannot book at this time. Please try again later.");
			}
		}
	}

	private boolean isPath1Available(ReservationQuery request) {
		LOG.info("Checking Path1");
		Timeout timeout = new Timeout(Duration.create(2, TimeUnit.MINUTES));

		HoldRequest hQuery = new HoldRequest("CA001", request.transactionId);
		Response holdResponse = null;
		try {
			holdResponse = (Response) FutureConverters.toJava(Patterns.ask(actorAA, hQuery, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();
		} catch (Exception e) {

			logErrorResponse(e.getMessage(), HOLD, request.transactionId);
		}

		if (holdResponse != null) {
			if (holdResponse.response.get(HomeController.STATUS).asText().equals(HomeController.ERROR)) {

				logErrorResponse(holdResponse.response.toString(), HOLD, request.transactionId);
			} else {
				ConfirmRequest cQuery = new ConfirmRequest("CA001", request.transactionId);
				Response confirmResponse = null;

				try {
					confirmResponse = (Response) FutureConverters.toJava(Patterns.ask(actorAA, cQuery, timeout))
							.thenApply(response -> (Response) response).toCompletableFuture().get();
				} catch (Exception e) {

					logErrorResponse(e.getMessage(), HOLD, request.transactionId);
				}

				if (confirmResponse != null) {
					if (confirmResponse.response.get(HomeController.STATUS).asText().equals(HomeController.SUCCESS)) {

						List<String> flights = new ArrayList<>();
						flights.add("CA001");

						int result = dataService.insertTransactions(flights, "CA001", request.transactionId);

						if (result == 0) {
							logErrorResponse("Error during persistence", CONFIRM, request.transactionId);
						} else {
							return true;
						}
					} else {

						logErrorResponse(confirmResponse.response.toString(), HOLD, request.transactionId);

					}
				}
			}
		}

		return false;

	}

	private boolean isPath2Available(ReservationQuery request) {
		LOG.info("Checking Path2");
		Timeout timeout = new Timeout(Duration.create(1, TimeUnit.MINUTES));

		HoldRequest hQuery1 = new HoldRequest("AA001", request.transactionId);
		HoldRequest hQuery2 = new HoldRequest("BA001", request.transactionId);
		Response holdResponse1 = null;
		Response holdResponse2 = null;
		try {
			holdResponse1 = FutureConverters.toJava(Patterns.ask(actorAA, hQuery1, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();

			holdResponse2 = FutureConverters.toJava(Patterns.ask(actorAA, hQuery2, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();



		} catch (Exception e) {

			logErrorResponse(e.getMessage(), HOLD, request.transactionId);
		}

		if (holdResponse1 != null && holdResponse2 != null) {
			if (holdResponse1.response.get(HomeController.STATUS).asText().equals(HomeController.ERROR)
					|| holdResponse2.response.get(HomeController.STATUS).asText().equals(HomeController.ERROR)) {

				logErrorResponse(holdResponse1.response.toString() + holdResponse2.response.toString(), HOLD,
						request.transactionId);
			} else {
				ConfirmRequest cQuery1 = new ConfirmRequest("AA001", request.transactionId);

				ConfirmRequest cQuery2 = new ConfirmRequest("BA001", request.transactionId);

				Response confirmResponse1 = null;

				Response confirmResponse2 = null;

				try {
					confirmResponse1 = FutureConverters.toJava(Patterns.ask(actorAA, cQuery1, timeout))
							.thenApply(response -> (Response) response).toCompletableFuture().get();

					confirmResponse2 = FutureConverters.toJava(Patterns.ask(actorAA, cQuery2, timeout))
							.thenApply(response -> (Response) response).toCompletableFuture().get();



				} catch (Exception e) {

					logErrorResponse(e.getMessage(), HOLD, request.transactionId);
				}

				if (confirmResponse1 != null && confirmResponse2 != null) {
					if (confirmResponse1.response.get(HomeController.STATUS).asText().equals(HomeController.SUCCESS)
							&& confirmResponse2.response.get(HomeController.STATUS).asText()
									.equals(HomeController.SUCCESS)) {
						List<String> flights = new ArrayList<>();

						flights.add("AA001");
						flights.add("BA001");

						int result = dataService.insertTransactions(flights, "AA001-BA001", request.transactionId);

						if (result == 0) {
							logErrorResponse("Error during persistence", CONFIRM, request.transactionId);
						} else {
							return true;
						}
					} else {
						logErrorResponse(confirmResponse1.response.toString() + confirmResponse2.response.toString(),
								HOLD, request.transactionId);
					}
				}
			}
		}

		return false;

	}

	private boolean isPath3Available(ReservationQuery request) {
		LOG.info("Checking Path3");
		Timeout timeout = new Timeout(Duration.create(1, TimeUnit.MINUTES));

		HoldRequest hQuery1 = new HoldRequest("AA001", request.transactionId);
		HoldRequest hQuery2 = new HoldRequest("CA002", request.transactionId);
		HoldRequest hQuery3 = new HoldRequest("aA001", request.transactionId);
		Response holdResponse1 = null;
		Response holdResponse2 = null;
		Response holdResponse3 = null;
		try {
			holdResponse1 = FutureConverters.toJava(Patterns.ask(actorAA, hQuery1, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();

			holdResponse2 = FutureConverters.toJava(Patterns.ask(actorAA, hQuery2, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();

			holdResponse3 = FutureConverters.toJava(Patterns.ask(actorAA, hQuery3, timeout))
					.thenApply(response -> (Response) response).toCompletableFuture().get();



		} catch (Exception e) {

			logErrorResponse(e.getMessage(), HOLD, request.transactionId);
		}

		if (holdResponse1 != null && holdResponse2 != null && holdResponse3 != null) {
			if (holdResponse1.response.get(HomeController.STATUS).asText().equals(HomeController.ERROR)
					|| holdResponse2.response.get(HomeController.STATUS).asText().equals(HomeController.ERROR)
					|| holdResponse3.response.get(HomeController.STATUS).asText().equals(HomeController.ERROR)) {

				logErrorResponse(holdResponse1.response.toString() + holdResponse2.response.toString()
						+ holdResponse3.response.toString(), HOLD,
						request.transactionId);
			} else {
				ConfirmRequest cQuery1 = new ConfirmRequest("AA001", request.transactionId);

				ConfirmRequest cQuery2 = new ConfirmRequest("CA002", request.transactionId);

				ConfirmRequest cQuery3 = new ConfirmRequest("AA002", request.transactionId);

				Response confirmResponse1 = null;

				Response confirmResponse2 = null;

				Response confirmResponse3 = null;

				try {
					confirmResponse1 = FutureConverters.toJava(Patterns.ask(actorAA, cQuery1, timeout))
							.thenApply(response -> (Response) response).toCompletableFuture().get();

					confirmResponse2 = FutureConverters.toJava(Patterns.ask(actorAA, cQuery2, timeout))
							.thenApply(response -> (Response) response).toCompletableFuture().get();

					confirmResponse3 = FutureConverters.toJava(Patterns.ask(actorAA, cQuery3, timeout))
							.thenApply(response -> (Response) response).toCompletableFuture().get();



				} catch (Exception e) {

					logErrorResponse(e.getMessage(), HOLD, request.transactionId);
				}

				if (confirmResponse1 != null && confirmResponse2 != null && confirmResponse3 != null) {
					if (confirmResponse1.response.get(HomeController.STATUS).asText().equals(HomeController.SUCCESS)
							&& confirmResponse2.response.get(HomeController.STATUS).asText()
									.equals(HomeController.SUCCESS)
							&& confirmResponse3.response.get(HomeController.STATUS).asText()
									.equals(HomeController.SUCCESS)) {
						List<String> flights = new ArrayList<>();

						flights.add("AA001");
						flights.add("CA002");
						flights.add("AA002");

						int result = dataService.insertTransactions(flights, "AA001-CA002-AA-002",
								request.transactionId);

						if (result == 0) {
							logErrorResponse("Error during persistence", CONFIRM, request.transactionId);
						} else {
							return true;
						}
					} else {
						logErrorResponse(confirmResponse1.response.toString() + confirmResponse2.response.toString()
								+ confirmResponse3.response.toString(),
								HOLD, request.transactionId);
					}
				}
			}
		}

		return false;

	}

	private void sendErrorResponse(String message) {
		ObjectNode obj = Json.newObject();

		obj.put(HomeController.MESSAGE, message);

		obj.put(HomeController.STATUS, HomeController.ERROR);

		Response res = new Response(obj);

		sender().tell(res, self());
	}

	private void logErrorResponse(String message, String action, long transactionId) {
		ObjectNode obj = Json.newObject();

		obj.put(HomeController.MESSAGE, message);

		obj.put(STATE, action);

		obj.put(HomeController.TRANSACTION_ID, transactionId);

		obj.put(HomeController.STATUS, HomeController.ERROR);

		LOG.error(obj.toString());
	}

}
