Updates the security settings for a specific CouchDB database. This endpoint controls which users and roles have **admin** or **member** access to the database.

## Endpoint

`PUT http://://_security`

## Authentication

Uses HTTP Basic Authentication embedded in the URL (`admin:admin`). The credentials should be replaced with appropriate admin credentials in non-local environments.

## Path

The target database in this request is `ofapoc_001$virgodarth$ofa_pms`, which follows the naming convention `$$`.

## Request Body

``` json
{
  "admins": {
    "names": [],
    "roles": []
  },
  "members": {
    "names": [
      "virgo"
    ],
    "roles": []
  }
}

 ```

| Field | Type | Description |
| --- | --- | --- |
| `admins.names` | array | List of CouchDB usernames granted admin access to the database. |
| `admins.roles` | array | List of CouchDB roles granted admin access to the database. |
| `members.names` | array | List of CouchDB usernames granted read/write member access. |
| `members.roles` | array | List of CouchDB roles granted read/write member access. |

## Notes

- Setting `admins` to empty arrays means only server-level admins can administer the database.

- The user `virgo` is granted member-level access, allowing them to read and write documents in the database.

- This request is typically run once during database provisioning or when access control needs to be updated.