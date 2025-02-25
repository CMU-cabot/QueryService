# QueryService

- set up directory and query service for NavCog
- need to add `query_server` key for server_config file for MapService

### server.env
- Development Configuration
    ```
    HULOP_MAP_SERVICE          # URL for the MapService (e.g., http://localhost:9090/map)
    HULOP_MAP_SERVICE_USE_HTTP # true: use HTTP - false: HTTPS
    ```
- Category Grouping Configuration
    ```
    # Specify the criteria for grouping categories
    # true: enabled - false: disabled (default)
    SEARCH_BY_BUILDING_ENABLED        # Enable grouping by building name
    SEARCH_BY_FLOOR_ENABLED           # Enable grouping by floor
    SEARCH_BY_CATEGORY_ENABLED        # Enable grouping by category name
    SEARCH_BY_NEARBY_FACILITY_ENABLED # Enable grouping by nearby facility
    ```

### hulop_messages.json (optional)
- Set unique keywords (such as category names) for each cabot_site
- On the MapServer, upload the `hulop_messages.json` file in `server_data/attachments/map/`
- Example
    ```jsx
    {
        "query_service_keys": {
            "Signature_Zone": {
                "en": "Signature Zone",
                "ja": "シグネチャーゾーン"
            },
            "Empowering_Zone": {
                "en": "Empowering Zone",
                "ja": "エンパワーリングゾーン"
            }
        }
    }
    ```