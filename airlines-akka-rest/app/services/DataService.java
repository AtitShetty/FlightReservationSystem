package services;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import akka.actor.ActorSystem;
import play.Logger;
import play.api.db.Database;
import play.libs.concurrent.CustomExecutionContext;

@Singleton
public class DataService {

	private static final Logger.ALogger LOG = Logger.of(DataService.class);

	private Database db;
	private DatabaseExecutionContext executionContext;

	@Inject
	public DataService(Database db, DatabaseExecutionContext context) {
		this.db = db;
		this.executionContext = context;
	}

	public List<String> getTripDetails(String tripId) {

		try {
			return CompletableFuture.supplyAsync(() -> {
				List<String> result = new ArrayList<>();
				String query;

				PreparedStatement st;
				try {
					if (tripId.equals("")) {
						query = "SELECT TRANSACTION_ID FROM TRANSACTIONS ORDER BY TRANSACTION_ID ASC";

						st = db.getConnection().prepareStatement(query);

					} else {
						query = "SELECT SEGMENT FROM TRANSACTIONS WHERE TRANSACTION_ID=? ORDER BY TRANSACTION_ID ASC";

						st = db.getConnection().prepareStatement(query);

						st.setString(1, tripId);
					}

					ResultSet rs = st.executeQuery();

					while (rs.next()) {
						result.add(rs.getString(1));
					}
				} catch (SQLException e) {
					result = null;

					LOG.error("Error executing trip query" + e.getMessage());
				}

				return result;
			}, executionContext).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Error", e);
			return null;
		}
	}

	public Collection<String> getOperatorDetails(String returnParam, String queryParam, List<String> paramValues) {
		try {
			return CompletableFuture.supplyAsync(() -> {

				HashSet<String> result = new HashSet<>();
				StringBuilder query = new StringBuilder();

				PreparedStatement st;
				try {

					query.append("SELECT " + returnParam + " FROM AIRLINEINFO ");

					if (queryParam != null) {
						query.append("WHERE " + queryParam + " ");
					}

					query.append("ORDER BY ID ASC");

					st = db.getConnection().prepareStatement(query.toString());

					if (paramValues != null) {
						for (int i = 0; i < paramValues.size(); i++) {
							st.setString(i + 1, paramValues.get(i));
						}
					}

					ResultSet rs = st.executeQuery();

					while (rs.next()) {
						result.add(rs.getString(1));
					}
				} catch (SQLException e) {
					result = null;

					LOG.error("Error processing operator query", e);
				}

				return result;
			}, executionContext).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Error", e);
			return null;
		}

	}

	public Integer getCurrentFlightCapacity(String flightNumber) {
		try {
			return CompletableFuture.supplyAsync(() -> {

				Integer result = null;
				StringBuilder query = new StringBuilder();

				PreparedStatement st;
				try {

					query.append("SELECT COUNT(TRANSACTION_ID) FROM TRANSACTIONS WHERE FLIGHT_NO=?");

					st = db.getConnection().prepareStatement(query.toString());

					st.setString(1, flightNumber);

					ResultSet rs = st.executeQuery();

					if (rs.next()) {
						result = rs.getInt(1);
					}
				} catch (SQLException e) {
					result = null;

					LOG.error("Error retrieving occupied flight seats", e);
				}

				return result;
			}, executionContext).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Error", e);
			return null;
		}

	}

	public Integer getTotalFlightCapacity(String flightNumber) {
		try {
			return CompletableFuture.supplyAsync(() -> {

				Integer result = null;
				StringBuilder query = new StringBuilder();

				PreparedStatement st;
				try {

					query.append("SELECT CAPACITY FROM AIRLINEINFO WHERE FLIGHT_NO=?");

					st = db.getConnection().prepareStatement(query.toString());

					st.setString(1, flightNumber);

					ResultSet rs = st.executeQuery();

					if (rs.next()) {
						result = rs.getInt(1);
					}
				} catch (SQLException e) {
					result = null;

					LOG.error("Error retrieving flight capacity", e);
				}

				return result;
			}, executionContext).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Error", e);
			return null;
		}

	}

	public int insertTransactions(List<String> flighNos, String segment, Long transactionId) {
		try {
			return CompletableFuture.supplyAsync(() -> {
				StringBuilder query = new StringBuilder();

				PreparedStatement st;
				try {

					query.append("INSERT INTO TRANSACTIONS (TRANSACTION_ID,FLIGHT_NO,SEGMENT) VALUES (?,?,?)");

					for (String flight : flighNos) {
						st = db.getConnection().prepareStatement(query.toString());
						st.setLong(1, transactionId);
						st.setString(2, flight);
						st.setString(3, segment);

						st.execute();

						st = null;
					}

				} catch (SQLException e) {

					LOG.error("Error retrieving flight capacity", e);

					return 0;
				}

				return 1;
			}, executionContext).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Error", e);
			return 0;
		}

	}

	public int clearTransactions() {
		try {
			return CompletableFuture.supplyAsync(() -> {
				StringBuilder query = new StringBuilder();

				PreparedStatement st;
				try {

					query.append("DELETE FROM TRANSACTIONS WHERE ID > 0");
					st = db.getConnection().prepareStatement(query.toString());
					st.execute();

				} catch (SQLException e) {

					LOG.error("Error DELETING transacations", e);

					return 0;
				}

				return 1;
			}, executionContext).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Error", e);
			return 0;
		}

	}

	public static class DatabaseExecutionContext extends CustomExecutionContext {

		@javax.inject.Inject
		public DatabaseExecutionContext(ActorSystem actorSystem) {
			// uses a custom thread pool defined in application.conf
			super(actorSystem, "database.dispatcher");
		}
	}

}
