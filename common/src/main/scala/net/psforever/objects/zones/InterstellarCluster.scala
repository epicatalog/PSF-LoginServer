// Copyright (c) 2017 PSForever
package net.psforever.objects.zones

import akka.actor.{Actor, Props}

import scala.annotation.tailrec

/**
  * The root of the universe of one-continent planets, codified by the game's "Interstellar Map."
  * Constructs each zone and thus instigates the construction of every server object in the game world.
  * The nanite flow connecting all of these `Zone`s is called the "Intercontinental Lattice."<br>
  * <br>
  * The process of "construction" and "initialization" and "configuration" are referenced at this level.
  * These concepts are not the same thing;
  * the distinction is important.
  * "Construction" and "instantiation" of the cluster merely produces the "facade" of the different `Zone` entities.
  * In such a `List`, every built `Zone` is capable of being a destination on the "Intercontinental lattice."
  * "Initialization" and "configuration" of the cluster refers to the act of completing the "Intercontinental Lattice"
  * by connecting different terminus warp gates together.
  * Other activities involve event management and managing wide-reaching and factional attributes.
  * @param zones a `List` of continental `Zone` arenas
  */
class InterstellarCluster(zones : List[Zone]) extends Actor {
  private[this] val log = org.log4s.getLogger
  log.info("Starting interplanetary cluster ...")

  /**
    * Create a `ZoneActor` for each `Zone`.
    * That `Actor` is sent a packet that would start the construction of the `Zone`'s server objects.
    * The process is maintained this way to allow every planet to be created and configured in separate stages.
    */
  override def preStart() : Unit = {
    super.preStart()
    for(zone <- zones) {
      log.info(s"Built continent ${zone.Id}")
      zone.Actor = context.actorOf(Props(classOf[ZoneActor], zone), s"${zone.Id}-actor")
      zone.Actor ! Zone.Init()
    }
  }

  def receive : Receive = {
    case InterstellarCluster.GetWorld(zoneId) =>
      log.info(s"Asked to find $zoneId")
      recursiveFindWorldInCluster(zones.iterator, _.Id == zoneId) match {
        case Some(continent) =>
          sender ! InterstellarCluster.GiveWorld(zoneId, continent)
        case None =>
          log.error(s"Requested zone $zoneId could not be found")
      }

    case InterstellarCluster.RequestClientInitialization() =>
      zones.foreach(zone => { sender ! Zone.ClientInitialization(zone.ClientInitialization()) })
      sender ! InterstellarCluster.ClientInitializationComplete() //will be processed after all Zones

    case msg @ Zone.Lattice.RequestSpawnPoint(zone_number, _, _) =>
      recursiveFindWorldInCluster(zones.iterator, _.Number == zone_number) match {
        case Some(zone) =>
          zone.Actor forward msg

        case None => //zone_number does not exist
          sender ! Zone.Lattice.NoValidSpawnPoint(zone_number, None)
      }

    case _ => ;
  }

  /**
    * Search through the `List` of `Zone` entities and find the one with the matching designation.
    * @param iter an `Iterator` of `Zone` entities
    * @param predicate a condition to check against to determine when the appropriate `Zone` is discovered
    * @return the discovered `Zone`
    */
  @tailrec private def recursiveFindWorldInCluster(iter : Iterator[Zone], predicate : Zone=>Boolean) : Option[Zone] = {
    if(!iter.hasNext) {
      None
    }
    else {
      val cont = iter.next
      if(predicate.apply(cont)) {
        Some(cont)
      }
      else {
        recursiveFindWorldInCluster(iter, predicate)
      }
    }
  }
}

object InterstellarCluster {

  /**
    * Request a hard reference to a `Zone`.
    * @param zoneId the name of the `Zone`
    */
  final case class GetWorld(zoneId : String)

  /**
    * Provide a hard reference to a `Zone`.
    * @param zoneId the name of the `Zone`
    * @param zone the `Zone`
    */
  final case class GiveWorld(zoneId : String, zone : Zone)

  /**
    * Signal to the cluster that a new client needs to be initialized for all listed `Zone` destinations.
    * @see `Zone`
    */
  final case class RequestClientInitialization()

  /**
    * Return signal intended to inform the original sender that all `Zone`s have finished being initialized.
    * @see `WorldSessionActor`
    */
  final case class ClientInitializationComplete()
}

/*
// List[Building] --> List[List[(Amenity, Building)]] --> List[(SpawnTube*, Building)]
zone.LocalLattice.Buildings.values
  .filter(_.Faction == player.Faction)
  .map(building => { building.Amenities.map { _ -> building } })
  .flatMap( _.filter({ case(amenity, _) => amenity.isInstanceOf[SpawnTube] }) )
 */

/*
zone.Buildings.values.filter(building => {
  (
    if(spawn_zone == 6) { Set(StructureType.Tower) }
    else if(spawn_zone == 7) { Set(StructureType.Facility, StructureType.Building) }
    else { Set.empty[StructureType.Value] }
    ).contains(building.BuildingType) &&
    building.Amenities.exists(_.isInstanceOf[SpawnTube]) &&
    building.Faction == player.Faction &&
    building.Position != Vector3.Zero
})
  .toSeq
  .sortBy(building => {
    Vector3.DistanceSquared(player.Position, building.Position) < Vector3.DistanceSquared(player.Position, building.Position)
  })
  .map(building => { building.Amenities.map { _ -> building } })
  .flatMap( _.filter({ case(amenity, _) => amenity.isInstanceOf[SpawnTube] }) )
).headOption
 */
