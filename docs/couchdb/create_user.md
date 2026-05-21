## Create User

Creates a new user in the CouchDB `_users` database. CouchDB uses a special `_users` system database to manage authentication and user accounts.

---

### Authentication

This request uses **Basic Auth**, configured in the Authorization tab. Provide your CouchDB admin credentials (username and password) there.

---

### Endpoint

```
PUT /_users/org.couchdb.user:{username}

 ```

The document ID must follow the format `org.couchdb.user:{username}` — this is required by CouchDB for user documents.

---

### Request Headers

| Header | Value |
| --- | --- |
| Content-Type | application/json |

---

### Request Body

``` json
{
  "name": "virgo123",
  "password": "virgo123",
  "roles": ["user"],
  "type": "user"
}

 ```

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | The username. Must match the username in the document ID. |
| `password` | string | The user's plain-text password. CouchDB will hash it automatically. |
| `roles` | array | List of roles assigned to the user (e.g. `"user"`, `"admin"`). |
| `type` | string | Must be `"user"` for all CouchDB user documents. |

---

### Response

**201 Created**

``` json
{
  "ok": true,
  "id": "org.couchdb.user:virgo123",
  "rev": "1-977cb861e38234b832d39a2f8e856dc9"
}

 ```

| Field | Description |
| --- | --- |
| `ok` | `true` if the user was created successfully. |
| `id` | The document ID of the newly created user. |
| `rev` | The revision ID of the document. |

---

**409 Conflict**

``` json
{
  "error": "conflict",
  "reason": "Document update conflict."
}

 ```

Returned when a user with the same document ID already exists in the `_users` database. To update an existing user, include the current `_rev` value in the request body.