package modules;

import com.google.inject.AbstractModule;

import actors.ActorAA;
import actors.ActorBooking;
import play.libs.akka.AkkaGuiceSupport;

public class ActorModule extends AbstractModule implements AkkaGuiceSupport {

	@Override
	protected void configure() {
		bindActor(ActorAA.class, "actorAA");
		bindActor(ActorBooking.class, "actorBooking");
	}

}
