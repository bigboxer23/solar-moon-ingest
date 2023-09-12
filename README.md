[![CodeQL](https://github.com/bigboxer23/solar-moon-ingest/actions/workflows/codeQL.yml/badge.svg)](https://github.com/bigboxer23/solar-moon-ingest/actions/workflows/codeQL.yml)

# solar-moon-ingest

This project is for collecting data from an Elkor Watts On Meter and stashing it into an open search cluster for
further analytics and dashboards.  It supports limiting the total fields we place into OS, as well as defining a
site for the device.

# Adding devices

There needs to be a servers.json file added which is located next to the current working path this is run on (`user.dir`).
This file should contain a list of servers which define specific devices to gather information from.  There is also the
concept of a "site", which is a virtual device that will aggregate all the single devices which have a matching site
attribute into a single "site" device.

An example of this file's content should look like:

```{
{
  "servers": [
    {
        "name": "Server A1",
        "address": "http://xxx.xxx.xxx.xxx/setup/devicexml.cgi?ADDRESS=xxx&TYPE=DATA",
        "site": "Site A"
    }
    ],
    "sites": [
    {
      "name" : "Site A"
    }
  ]
}
```

