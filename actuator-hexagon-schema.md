# `/actuator/hexagon` — Endpunkt-Kontrakt v1.0.0

Entwurf für einen Spring-Boot-Starter, der die hexagonale Struktur eines Service zur
Laufzeit selbst beschreibt, sowie für den Collector, der daraus eine Landschaftskarte baut.

---

## Designprinzipien

Diese fünf Entscheidungen legen alles Weitere fest. Sie sind der eigentliche Inhalt
dieses Dokuments — das JSON ist nur ihre Konsequenz.

**1. Struktur, keine Metriken.**
Der Endpunkt beschreibt, *wie der Service gebaut ist*, nicht wie es ihm geht. Latenzen,
Fehlerraten und Traces gehören zu Micrometer und OpenTelemetry. Sobald du Laufzeitdaten
mit hineinnimmst, wird die Antwort teuer, nicht cachebar und du konkurrierst mit
Observability-Tools, die das besser können.
Folge: Die Antwort wird **einmal beim Start berechnet** und danach nur noch ausgeliefert.

**2. Stabile IDs, oder das Ganze ist wertlos.**
Jede ID muss über Neustarts, Instanzen und Deployments identisch bleiben — sonst kann der
Collector nichts über die Zeit vergleichen und jede Karte sieht nach jedem Deployment neu
aus. Deshalb: **vollqualifizierter Klassenname** als ID, niemals ein Objekt-Hash, ein
Bean-Name oder eine laufende Nummer.

**3. Alles außer `id` und `direction` ist optional.**
Der Starter muss auch in einem Service funktionieren, der keine einzige Annotation trägt
und nur Paketkonventionen folgt. Ein Service, der nur die Hälfte weiß, liefert die Hälfte —
er liefert keinen Fehler. Das ist die Voraussetzung dafür, dass du das Ding schrittweise
in eine bestehende Landschaft einführen kannst.

**4. `provenance` ist Pflicht, nicht Beiwerk.**
Jedes Element sagt, *woher* die Information stammt: aus einer Annotation, aus einer
Paketkonvention oder aus Laufzeit-Inspektion. Damit kann die UI Geratenes gestrichelt und
Deklariertes durchgezogen zeichnen. Ein Tool, das Vermutungen als Fakten darstellt,
verliert genau einmal Vertrauen.

**5. Keine Geheimnisse nach draußen.**
Kontaktpunkte werden normalisiert: Credentials, Query-Parameter und konkrete IDs fliegen
raus, Pfade werden als Template dargestellt (`/api/orders/{id}`, nicht `/api/orders/4711`).
Der Endpunkt ist ein Actuator-Endpunkt und damit potenziell exponiert — er darf nichts
enthalten, was nicht auch in ein Architekturdiagramm dürfte.

---

## Aufbau der Antwort

```
{
  schemaVersion, generatedAt
  service   → wer bin ich
  core      → was ist drin (Domäne)
  ports     → welche Löcher hat das Hexagon
  adapters  → was steckt in den Löchern, und was berührt es draußen
}
```

Die Kanten der Landschaft entstehen **nicht** hier. Sie entstehen im Collector durch das
Zusammenführen der `contactPoints` aller Services. Kein Service weiß, wer ihn aufruft —
und soll es auch nicht wissen müssen. Das ist der Punkt, an dem sich dieser Ansatz von
einem gepflegten Backstage-Katalog unterscheidet.

---

## Beispiel-Payload

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "2026-08-07T09:14:22Z",

  "service": {
    "id": "orders-service",
    "displayName": "Orders",
    "version": "3.4.1",
    "environment": "prod",
    "instanceId": "orders-service-7d9f4c-x2k",
    "basePackage": "com.acme.orders",
    "repository": "https://github.com/acme/orders-service"
  },

  "core": {
    "basePackage": "com.acme.orders.domain",
    "aggregates": [
      { "id": "com.acme.orders.domain.Order", "name": "Order", "provenance": "ANNOTATION" }
    ],
    "events": {
      "published": [
        { "id": "com.acme.orders.domain.event.OrderPlaced", "name": "OrderPlaced" }
      ],
      "consumed": [
        { "id": "com.acme.shipping.event.ShipmentDispatched", "name": "ShipmentDispatched" }
      ]
    }
  },

  "ports": [
    {
      "id": "com.acme.orders.application.port.in.PlaceOrderUseCase",
      "name": "PlaceOrder",
      "direction": "PRIMARY",
      "provenance": "ANNOTATION",
      "operations": ["placeOrder"]
    },
    {
      "id": "com.acme.orders.application.port.out.InventoryPort",
      "name": "Inventory",
      "direction": "SECONDARY",
      "provenance": "ANNOTATION",
      "operations": ["reserveStock", "releaseStock"]
    },
    {
      "id": "com.acme.orders.application.port.out.OrderRepository",
      "name": "OrderRepository",
      "direction": "SECONDARY",
      "provenance": "CONVENTION"
    }
  ],

  "adapters": [
    {
      "id": "com.acme.orders.adapter.in.web.OrderController",
      "name": "Order REST API",
      "direction": "PRIMARY",
      "technology": "spring-web",
      "provenance": "ANNOTATION",
      "implementsPorts": ["com.acme.orders.application.port.in.PlaceOrderUseCase"],
      "contactPoints": [
        {
          "key": "http:POST /api/orders",
          "protocol": "HTTP",
          "direction": "INBOUND",
          "confidence": "HIGH",
          "attributes": { "method": "POST", "pathTemplate": "/api/orders" }
        }
      ]
    },
    {
      "id": "com.acme.orders.adapter.out.inventory.InventoryRestClient",
      "name": "Inventory Client",
      "direction": "SECONDARY",
      "technology": "spring-webclient",
      "provenance": "ANNOTATION",
      "implementsPorts": ["com.acme.orders.application.port.out.InventoryPort"],
      "contactPoints": [
        {
          "key": "http:inventory-service:POST /api/items/{sku}/reservations",
          "protocol": "HTTP",
          "direction": "OUTBOUND",
          "confidence": "MEDIUM",
          "target": {
            "logicalService": "inventory-service",
            "resolution": "SERVICE_DISCOVERY"
          },
          "attributes": {
            "method": "POST",
            "pathTemplate": "/api/items/{sku}/reservations"
          }
        }
      ]
    },
    {
      "id": "com.acme.orders.adapter.out.messaging.OrderEventPublisher",
      "name": "Order Events",
      "direction": "SECONDARY",
      "technology": "spring-kafka",
      "provenance": "ANNOTATION",
      "contactPoints": [
        {
          "key": "kafka:topic/orders.placed",
          "protocol": "KAFKA",
          "direction": "OUTBOUND",
          "confidence": "HIGH",
          "attributes": { "topic": "orders.placed" }
        }
      ]
    },
    {
      "id": "com.acme.orders.adapter.out.persistence.JpaOrderRepository",
      "name": "Order Persistence",
      "direction": "SECONDARY",
      "technology": "spring-data-jpa",
      "provenance": "CONVENTION",
      "implementsPorts": ["com.acme.orders.application.port.out.OrderRepository"],
      "contactPoints": [
        {
          "key": "jdbc:postgresql/orders",
          "protocol": "JDBC",
          "direction": "OUTBOUND",
          "confidence": "HIGH",
          "attributes": { "vendor": "postgresql", "database": "orders" }
        }
      ]
    }
  ]
}
```

---

## Feldreferenz

### `service`

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `id` | ja | Stabiler logischer Name. Knotenidentität in der Landschaft. Default: `spring.application.name` |
| `version` | nein | Für Drift über Deployments hinweg |
| `environment` | nein | Trennt Landschaften; sonst mischst du Staging und Prod in einer Karte |
| `instanceId` | nein | Nur zur Deduplizierung mehrerer Instanzen desselben Service |

### `ports[]`

| Feld | Pflicht | Werte |
|---|---|---|
| `id` | ja | FQCN des Interface |
| `direction` | ja | `PRIMARY` (treibend) \| `SECONDARY` (getrieben) |
| `provenance` | ja | `ANNOTATION` \| `CONVENTION` \| `RUNTIME` |
| `operations` | nein | Methodennamen, für den Drill-down |

### `adapters[]`

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `id` | ja | FQCN der Adapterklasse |
| `direction` | ja | `PRIMARY` \| `SECONDARY` |
| `technology` | nein | Freitext-Kennung (`spring-web`, `spring-kafka`, …) — steuert das Icon |
| `implementsPorts` | nein | Verweise auf `ports[].id`. Leer = Adapter am Port vorbei → Architekturverstoß, den die UI markieren kann |
| `contactPoints` | nein | Berührungspunkte nach außen |

### `contactPoints[]`

Das ist das Herzstück — hier entstehen später die Kanten.

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `key` | ja | Kanonischer Matching-Schlüssel, siehe unten |
| `protocol` | ja | `HTTP` \| `KAFKA` \| `AMQP` \| `JDBC` \| `GRPC` \| `FILE` \| `OTHER` |
| `direction` | ja | `INBOUND` \| `OUTBOUND` |
| `confidence` | ja | `HIGH` \| `MEDIUM` \| `LOW` — wie sicher ist der Schlüssel? |
| `target` | nein | Nur bei `OUTBOUND`: wohin |
| `attributes` | nein | Protokollspezifische Details für den Drill-down |

### Kanonische Schlüssel

Der Schlüssel ist der gesamte Matching-Vertrag. Alles hängt daran, dass zwei Services
unabhängig voneinander denselben String bilden.

| Protokoll | Muster | Beispiel |
|---|---|---|
| HTTP inbound | `http:{METHODE} {pathTemplate}` | `http:POST /api/orders` |
| HTTP outbound | `http:{zielService}:{METHODE} {pathTemplate}` | `http:inventory-service:GET /api/items/{sku}` |
| Kafka | `kafka:topic/{topic}` | `kafka:topic/orders.placed` |
| AMQP | `amqp:exchange/{exchange}` | `amqp:exchange/billing` |
| JDBC | `jdbc:{vendor}/{datenbank}` | `jdbc:postgresql/orders` |

Regeln zur Normalisierung: Pfadvariablen immer als `{name}`, Methode in Großbuchstaben,
kein Trailing Slash, keine Query-Parameter, kein Host im Pfadteil.

---

## Wie der Collector daraus Kanten macht

1. **Exakter Treffer, gegenläufige Richtung** → bestätigte Kante, durchgezogen.
   `http:inventory-service:GET /api/items/{sku}` trifft bei `inventory-service` auf
   `http:GET /api/items/{sku}`.
2. **Kafka Produce/Consume auf demselben Topic** → bestätigte Kante. Der zuverlässigste
   Fall überhaupt, weil beide Seiten denselben String bilden, ohne etwas voneinander zu wissen.
3. **Outbound ohne aufgelösten Zielservice**, aber das Pfad-Template passt genau einmal
   irgendwo in der Landschaft → wahrscheinliche Kante, gestrichelt.
4. **Zwei Services mit demselben `jdbc:`-Schlüssel** → keine Kante, sondern ein Befund:
   geteilte Datenbank. Das ist eines der wertvollsten Dinge, die dein Tool zeigen kann,
   weil genau diese Kopplung in keinem Architekturdiagramm steht.
5. **Outbound ohne jeden Treffer** → externer Knoten am Rand der Landschaft.

---

## Was bewusst nicht drin ist

- **Ownership, Teams, Bereitschaftsdienst** — Backstage-Territorium
- **Laufzeitmetriken** — siehe Prinzip 1
- **Regelverstöße** (`violations[]`) — reizvoll, aber erst nach dem MVP. ArchUnit prüft das
  zur Buildzeit besser, und du müsstest dich auf eine Regelsemantik festlegen, bevor du
  weißt, ob überhaupt jemand das Tool benutzt.

---

## Offene Entscheidungen

1. **Auflösung des Zielservice bei HTTP-Outbound.** Ohne Service Discovery hast du nur
   einen Hostnamen. Optionen: explizite Annotation am Adapter, Konfigurationsmapping
   `hexagon.targets.inventory.internal=inventory-service`, oder Auflösung erst im Collector
   über eine zentrale Hostliste. Das Letztere skaliert am besten, verlagert aber Konfiguration
   dorthin, wo du sie eigentlich loswerden wolltest.
2. **Scan zur Startzeit vs. Buildzeit.** Startzeit ist bequemer, kostet aber Startup-Zeit
   und braucht Classpath-Scanning zur Laufzeit — unschön bei GraalVM-Native-Images. Ein
   Annotation Processor, der ein Manifest ins JAR legt, wäre sauberer und schwerer zu bauen.
3. **Aggregation mehrerer Instanzen.** Zehn Pods desselben Service liefern zehn identische
   Antworten. Dedupliziert der Collector über `service.id` + `version`, oder pollt er
   überhaupt nur eine Instanz pro Service?

Punkt 2 solltest du früh entscheiden — sie ist im Nachhinein teuer.
