package vn.com.primetech.ofagw.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>OFA Gateway - Route Monitor</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
                    <style>
                        :root {
                            --bg-primary: #0f1117;
                            --bg-secondary: #1a1d27;
                            --bg-card: #242836;
                            --text-primary: #f8fafc;
                            --text-secondary: #94a3b8;
                            --accent: #6366f1;
                            --accent-light: #818cf8;
                            --success: #22c55e;
                            --warning: #f59e0b;
                            --border: #334155;
                        }

                        * { margin: 0; padding: 0; box-sizing: border-box; }

                        body {
                            font-family: 'Inter', system-ui, sans-serif;
                            background: var(--bg-primary);
                            color: var(--text-primary);
                            min-height: 100vh;
                            overflow-x: hidden;
                        }

                        .bg-grid {
                            position: fixed;
                            inset: 0;
                            background-image: 
                                linear-gradient(rgba(99, 102, 241, 0.03) 1px, transparent 1px),
                                linear-gradient(90deg, rgba(99, 102, 241, 0.03) 1px, transparent 1px);
                            background-size: 60px 60px;
                            pointer-events: none;
                            z-index: 0;
                        }

                        .bg-glow {
                            position: fixed;
                            width: 600px;
                            height: 600px;
                            background: radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, transparent 70%);
                            top: -200px;
                            right: -200px;
                            pointer-events: none;
                            z-index: 0;
                        }

                        .container {
                            position: relative;
                            z-index: 1;
                            max-width: 1200px;
                            margin: 0 auto;
                            padding: 48px 24px;
                        }

                        header {
                            text-align: center;
                            margin-bottom: 64px;
                        }

                        .logo {
                            display: inline-flex;
                            align-items: center;
                            gap: 12px;
                            margin-bottom: 16px;
                        }

                        .logo-icon {
                            width: 56px;
                            height: 56px;
                            background: linear-gradient(135deg, var(--accent), var(--accent-light));
                            border-radius: 16px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 24px;
                            font-weight: 700;
                            color: white;
                            box-shadow: 0 8px 32px rgba(99, 102, 241, 0.3);
                        }

                        h1 {
                            font-size: 2.5rem;
                            font-weight: 700;
                            background: linear-gradient(135deg, var(--text-primary) 0%, var(--text-secondary) 100%);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                            background-clip: text;
                            margin-bottom: 8px;
                        }

                        .subtitle {
                            color: var(--text-secondary);
                            font-size: 1.1rem;
                        }

                        .status-bar {
                            display: flex;
                            justify-content: center;
                            gap: 32px;
                            margin-bottom: 48px;
                            flex-wrap: wrap;
                        }

                        .status-item {
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            padding: 12px 20px;
                            background: var(--bg-secondary);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                        }

                        .status-dot {
                            width: 10px;
                            height: 10px;
                            border-radius: 50%;
                            background: var(--success);
                            animation: pulse 2s infinite;
                        }

                        @keyframes pulse {
                            0%, 100% { opacity: 1; }
                            50% { opacity: 0.5; }
                        }

                        .routes-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
                            gap: 24px;
                        }

                        .route-card {
                            background: var(--bg-card);
                            border: 1px solid var(--border);
                            border-radius: 16px;
                            padding: 24px;
                            transition: all 0.3s ease;
                            position: relative;
                            overflow: hidden;
                            text-decoration: none;
                            display: block;
                            cursor: pointer;
                        }

                        .route-card:hover {
                            border-color: var(--accent);
                            transform: translateY(-4px);
                            box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
                        }

                        .route-card::before {
                            content: '';
                            position: absolute;
                            top: 0;
                            left: 0;
                            right: 0;
                            height: 3px;
                            background: linear-gradient(90deg, var(--accent), var(--accent-light));
                            opacity: 0;
                            transition: opacity 0.3s ease;
                        }

                        .route-card:hover::before { opacity: 1; }

                        .route-header {
                            display: flex;
                            align-items: flex-start;
                            justify-content: space-between;
                            margin-bottom: 16px;
                        }

                        .route-method {
                            padding: 6px 12px;
                            border-radius: 8px;
                            font-size: 0.75rem;
                            font-weight: 600;
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                        }

                        .method-get {
                            background: rgba(34, 197, 94, 0.15);
                            color: var(--success);
                        }

                        .method-post {
                            background: rgba(99, 102, 241, 0.15);
                            color: var(--accent-light);
                        }

                        .route-id {
                            font-size: 0.75rem;
                            color: var(--text-secondary);
                            background: var(--bg-secondary);
                            padding: 4px 8px;
                            border-radius: 6px;
                        }

                        .route-path {
                            font-size: 1.25rem;
                            font-weight: 600;
                            margin-bottom: 12px;
                            font-family: 'SF Mono', 'Fira Code', monospace;
                            color: var(--text-primary);
                        }

                        .route-destination {
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            color: var(--text-secondary);
                            font-size: 0.875rem;
                        }

                        .route-destination svg {
                            width: 16px;
                            height: 16px;
                            opacity: 0.7;
                        }

                        .route-filters {
                            margin-top: 16px;
                            padding-top: 16px;
                            border-top: 1px solid var(--border);
                        }

                        .filter-label {
                            font-size: 0.7rem;
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                            color: var(--text-secondary);
                            margin-bottom: 8px;
                        }

                        .filter-tags {
                            display: flex;
                            flex-wrap: wrap;
                            gap: 6px;
                        }

                        .filter-tag {
                            padding: 4px 10px;
                            background: var(--bg-secondary);
                            border-radius: 6px;
                            font-size: 0.75rem;
                            color: var(--text-secondary);
                        }

                        .endpoints-section {
                            margin-top: 64px;
                        }

                        .section-title {
                            font-size: 1.5rem;
                            font-weight: 600;
                            margin-bottom: 24px;
                            display: flex;
                            align-items: center;
                            gap: 12px;
                        }

                        .section-title::before {
                            content: '';
                            width: 4px;
                            height: 24px;
                            background: var(--accent);
                            border-radius: 2px;
                        }

                        .endpoint-list {
                            display: flex;
                            flex-direction: column;
                            gap: 12px;
                        }

                        .endpoint-item {
                            display: flex;
                            align-items: center;
                            gap: 16px;
                            padding: 16px 20px;
                            background: var(--bg-secondary);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            transition: all 0.2s ease;
                            text-decoration: none;
                            display: flex;
                        }

                        .endpoint-item:hover { border-color: var(--accent); }

                        .endpoint-path {
                            font-family: 'SF Mono', 'Fira Code', monospace;
                            font-size: 0.95rem;
                            color: var(--text-primary);
                        }

                        .endpoint-desc {
                            margin-left: auto;
                            color: var(--text-secondary);
                            font-size: 0.875rem;
                        }

                        .clickable { cursor: pointer; }

                        footer {
                            margin-top: 80px;
                            text-align: center;
                            color: var(--text-secondary);
                            font-size: 0.875rem;
                        }

                        @media (max-width: 640px) {
                            h1 { font-size: 1.75rem; }
                            .status-bar { gap: 16px; }
                            .routes-grid { grid-template-columns: 1fr; }
                        }
                    </style>
                </head>
                <body>
                    <div class="bg-grid"></div>
                    <div class="bg-glow"></div>

                    <div class="container">
                        <header>
                            <div class="logo">
                                <div class="logo-icon">GW</div>
                            </div>
                            <h1>OFA Gateway</h1>
                            <p class="subtitle">Route Configuration & Service Monitor</p>
                        </header>

                        <div class="status-bar">
                            <div class="status-item">
                                <div class="status-dot"></div>
                                <span>Gateway Active</span>
                            </div>
                            <div class="status-item">
                                <div class="status-dot"></div>
                                <span>Spring Cloud Gateway</span>
                            </div>
                            <div class="status-item">
                                <div class="status-dot"></div>
                                <span>v4.0.5</span>
                            </div>
                        </div>

                        <div class="routes-grid">
                            <a href="/auth/test" class="route-card">
                                <div class="route-header">
                                    <span class="route-method method-get">GET</span>
                                    <span class="route-id">auth-service</span>
                                </div>
                                <div class="route-path">/auth/**</div>
                                <div class="route-destination">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                        <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
                                    </svg>
                                    <span>http://localhost:8082/identity</span>
                                </div>
                                <div class="route-filters">
                                    <div class="filter-label">Filters</div>
                                    <div class="filter-tags">
                                        <span class="filter-tag">StripPrefix=1</span>
                                        <span class="filter-tag">DedupeResponseHeader</span>
                                        <span class="filter-tag">RemoveRequestHeader</span>
                                    </div>
                                </div>
                            </a>

                            <a href="/db/" class="route-card">
                                <div class="route-header">
                                    <span class="route-method method-post">DB</span>
                                    <span class="route-id">couchdb</span>
                                </div>
                                <div class="route-path">/db/**</div>
                                <div class="route-destination">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                        <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
                                    </svg>
                                    <span>http://localhost:5984</span>
                                </div>
                                <div class="route-filters">
                                    <div class="filter-label">Filters</div>
                                    <div class="filter-tags">
                                        <span class="filter-tag">StripPrefix=1</span>
                                        <span class="filter-tag">DedupeResponseHeader</span>
                                        <span class="filter-tag">RemoveRequestHeader</span>
                                    </div>
                                </div>
                            </a>
                        </div>

                        <div class="endpoints-section">
                            <h2 class="section-title">Management Endpoints</h2>
                            <div class="endpoint-list">
                                <a href="/actuator/health" class="endpoint-item">
                                    <span class="route-method method-get">GET</span>
                                    <span class="endpoint-path">/actuator/health</span>
                                    <span class="endpoint-desc">Health check</span>
                                </a>
                                <a href="/actuator/info" class="endpoint-item">
                                    <span class="route-method method-get">GET</span>
                                    <span class="endpoint-path">/actuator/info</span>
                                    <span class="endpoint-desc">Application info</span>
                                </a>
                                <a href="/actuator/gateway" class="endpoint-item">
                                    <span class="route-method method-get">GET</span>
                                    <span class="endpoint-path">/actuator/gateway</span>
                                    <span class="endpoint-desc">Gateway routes</span>
                                </a>
                                <a href="/actuator/metrics" class="endpoint-item">
                                    <span class="route-method method-get">GET</span>
                                    <span class="endpoint-path">/actuator/metrics</span>
                                    <span class="endpoint-desc">Metrics</span>
                                </a>
                            </div>
                        </div>

                        <footer>
                            <p>Spring Boot 4.0.5 &bull; Spring Cloud 2025.1.1 &bull; Java 25</p>
                        </footer>
                    </div>
                </body>
                </html>
                """;
    }
}
