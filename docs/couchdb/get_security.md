# Get DB Security

Retrieves the security object for a specific CouchDB database. The security object defines which users and roles have **admin** or **member** access to the database.

---

## Endpoint

| Property | Value |
| --- | --- |
| **Method** | `GET` |
| **URL** | `http://localhost:5984/ofapoc_001$virgodarth$ofa_pms/_security` |

---

## Authentication

Uses **HTTP Basic Auth** embedded directly in the URL:

- **Username:** `admin`

- **Password:** `admin`


> The credentials are passed as `http://admin:admin@localhost:5984/...`


---

## Target Database

```
ofapoc_001$virgodarth$ofa_pms

 ```

This is the CouchDB database whose security configuration is being queried.

---

## The `_security` Endpoint (CouchDB)

The `_security` endpoint is a special CouchDB endpoint available on every database. It returns the **security object**, which controls access at the database level by specifying:

- **`admins`** — Users and roles with full administrative access to the database (can read, write, and manage design documents).

- **`members`** — Users and roles with read/write access to the database.


If a field is empty, CouchDB falls back to server-level admin rules.

---

## Example Response

``` json
{
  "admins": {
    "names": [],
    "roles": []
  },
  "members": {
    "names": ["virgo"],
    "roles": []
  }
}

 ```

### Response Fields

| Field | Type | Description |
| --- | --- | --- |
| `admins.names` | `string[]` | List of usernames with admin access. Empty means no per-user admin restrictions. |
| `admins.roles` | `string[]` | List of roles with admin access. |
| `members.names` | `string[]` | List of usernames with member (read/write) access. Here: `["virgo"]`. |
| `members.roles` | `string[]` | List of roles with member access. |

---

## Notes

- An empty `admins` object means only server-level admins can administer the database.

- The user `virgo` is explicitly listed as a **member**, granting them access to this database.

- This endpoint requires **admin credentials** to read or modify the security object.

- The database name uses `$` as a delimiter, which is a common CouchDB naming convention for per-user or namespaced databases.