Creates a new CouchDB database with the specified name encoded in the URL path.

CouchDB uses a PUT request to create a database. The database name is derived from the last segment of the URL path. Database names must begin with a lowercase letter and can only contain lowercase letters (`a–z`), digits (`0–9`), and the characters `_`, `$`, `(`, `)`, `+`, `-`, and `/`.

**Authentication**

Requires CouchDB admin credentials. Ensure the `Authorization` header or session cookie is set appropriately.

**Responses**

| Status | Meaning |
| --- | --- |
| `201 Created` | Database was created successfully. |
| `400 Bad Request` | Invalid database name. |
| `401 Unauthorized` | Missing or invalid credentials. |
| `412 Precondition Failed` | A database with this name already exists. |

**Notes**

- This request has no request body.

- The database name in this request is `ofapoc_001$virgodarth$ofa_pms`.