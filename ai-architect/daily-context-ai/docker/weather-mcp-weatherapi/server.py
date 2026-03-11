import os
import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("weather-weatherapi", host="0.0.0.0", port=8080)

BASE_URL = "https://api.weatherapi.com/v1"
API_KEY = os.environ.get("WEATHERAPI_KEY", "")


@mcp.tool()
async def get_current_weather(location: str) -> str:
    """Get current weather for a location using WeatherAPI."""
    if not API_KEY:
        return "WeatherAPI key not configured. Set WEATHERAPI_KEY environment variable."

    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{BASE_URL}/current.json", params={
            "key": API_KEY,
            "q": location,
        })
        if resp.status_code == 401:
            return "Invalid WeatherAPI key."
        if resp.status_code == 400:
            return f"Location '{location}' not found."
        resp.raise_for_status()
        data = resp.json()

    loc = data["location"]
    current = data["current"]
    condition = current["condition"]["text"]
    return (
        f"Current weather in {loc['name']}, {loc['country']}:\n"
        f"  Condition: {condition}\n"
        f"  Temperature: {current['temp_c']}°C (feels like {current['feelslike_c']}°C)\n"
        f"  Humidity: {current['humidity']}%\n"
        f"  Wind: {current['wind_kph']} km/h {current['wind_dir']}\n"
        f"  UV Index: {current['uv']}"
    )


@mcp.tool()
async def get_weather_forecast(location: str, days: int = 3) -> str:
    """Get weather forecast for a location using WeatherAPI."""
    if not API_KEY:
        return "WeatherAPI key not configured. Set WEATHERAPI_KEY environment variable."

    days = max(1, min(days, 10))
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{BASE_URL}/forecast.json", params={
            "key": API_KEY,
            "q": location,
            "days": days,
        })
        if resp.status_code == 401:
            return "Invalid WeatherAPI key."
        if resp.status_code == 400:
            return f"Location '{location}' not found."
        resp.raise_for_status()
        data = resp.json()

    loc = data["location"]
    forecast_days = data["forecast"]["forecastday"]
    lines = [f"{days}-day forecast for {loc['name']}, {loc['country']}:"]
    for day in forecast_days:
        d = day["day"]
        lines.append(
            f"  {day['date']}: {d['mintemp_c']}-{d['maxtemp_c']}°C, "
            f"{d['condition']['text']}, "
            f"rain chance {d['daily_chance_of_rain']}%"
        )
    return "\n".join(lines)


mcp.run(transport="streamable-http")
