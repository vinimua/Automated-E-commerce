"""Integration tests — verify cross-service contracts.

These tests run in two modes:

  # 1. Local contract tests (always run, no external services needed)
  pytest tests/integration/ -v -k "not AgainstJava and not Integration"

  # 2. Full integration tests (need Java API running)
  JAVA_API_URL=http://localhost:8080 pytest tests/integration/ -v

  # 3. Skip integration tests in CI-only unit test runs
  pytest tests/ -v --ignore=tests/integration
"""
