package io.restall.gameon.room.groovy.roomservice

import com.google.inject.Inject
import groovy.json.JsonOutput
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import ratpack.service.Service
import ratpack.service.StartEvent

import java.time.Instant

class GameOnHttpService implements Service {

  @Inject
  private RoomServiceConfig config

  @Inject
  private Room room

  @Inject
  private SecurityService securityService

  @Override
  public void onStart(StartEvent event) {
//    if (alreadyRegistered()) {
//      throw new RuntimeException("user with id <$config.userId> already has a room running")
//    }
    registerApplication()
  }

  private boolean alreadyRegistered() {
    def client = new RESTClient("https://game-on.org/map/v1/sites?owner=$config.userId&name=$config.roomName")
    def resp = client.get([:])

    resp.status != 204
  }

  private void registerApplication() {
    def request = [name             : config.roomName,
                   fullName         : config.fullRoomName,
                   description      : config.roomDescription,
                   doors            : room.doors,
                   connectionDetails: [type  : 'websocket',
                                       target: "ws://$config.hostname/room"]]

    def json = JsonOutput.toJson(request).toString()

    def bodyHash = securityService.hash(json)
    def dateValue = Instant.now().toString()

    def hmac = securityService.hmacSHA256([config.userId, dateValue, bodyHash].join(''), config.key)
    def client = new RESTClient("https://game-on.org/map/v1/sites")

    try {
      def resp = client.post(body: json,
                             headers: ["gameon-id"       : config.userId,
                                       "gameon-date"     : dateValue,
                                       "gameon-sig-body" : bodyHash,
                                       "gameon-signature": hmac,
                                       "Accept"          : "application/json,text/plain",
                                       "Method"          : "POST",
                                       "User-Agent"      : "Java/1.8.0_60"],
                             requestContentType: 'application/json;')

      if (resp.status != 200 && resp.status != 201) {
        throw new RuntimeException("Unable to register application, response code: $resp.status")
      }
    } catch (HttpResponseException e) {
      println e.response.data
    }
  }


}
