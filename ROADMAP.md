# Orbis Trade AI

## Roadmap v1.0

Este documento governa o desenvolvimento do projeto.

### Regra Máxima
Nenhuma funcionalidade poderá ser desenvolvida fora do Sprint atual. Ideias novas entram apenas no Backlog.

## Sprint 1 - Infraestrutura ✅ VALIDADO EM DISPOSITIVO
- [x] Estrutura Android
- [x] Kotlin
- [x] Jetpack Compose
- [x] Clean Architecture
- [x] MVVM
- [x] Overlay
- [x] MediaProjection
- [x] Navegação

## Sprint 2 - Visão Computacional ✅ IMPLEMENTADO
- [x] OpenCV
- [x] Detecção experimental do gráfico
- [x] Reconstrução inicial de candidatos a candles
- [x] OCR dos textos visíveis

## Sprint 2.1 - Processamento em segundo plano ✅ VALIDADO EM DISPOSITIVO
- [x] Análise executada no foreground service enquanto outro aplicativo está em primeiro plano
- [x] Estado compartilhado independente da Activity
- [x] Overlay atualizado em tempo real
- [x] Diagnóstico persistente

## Sprint 2.2 - Robustez de escala e zoom 🧪 IMPLEMENTADO — AGUARDA VALIDAÇÃO
- [x] Detecção com limites proporcionais mais tolerantes
- [x] Candles estreitos em zoom reduzido
- [x] Margem proporcional na região do gráfico
- [x] Memória temporária da última região detectada para evitar oscilações

## Sprint 3 - Indicadores ✅ VALIDADO FUNCIONALMENTE — CALIBRAÇÃO CONTÍNUA
- [x] EMA 12
- [x] EMA 60
- [x] Bollinger 12 / 1,5
- [x] ATR 14
- [x] Lateralidade
- [x] Tendência
- [x] Volatilidade
- [x] Testes unitários

## Sprint 4 - Estratégia Dubai V1 ✅ VALIDADO FUNCIONALMENTE
- [x] Motor de regras
- [x] Score de 0 a 100
- [x] Alertas visuais
- [x] Histórico
- [x] Banco local SQLite
- [x] Bloqueio lateral
- [x] Cooldown
- [x] Testes de CALL, PUT e WAIT

## Sprint 5 - Estatísticas 🧪 IMPLEMENTADO — AGUARDA VALIDAÇÃO
- [x] Dashboard
- [x] Registro manual de WIN/LOSS
- [x] Win rate calculado apenas sobre resultados conferidos
- [x] Heatmap por horário
- [x] Melhor horário com amostra mínima
- [x] Score médio, CALL/PUT e pendências
- [x] Exportação CSV segura
- [x] Migração do banco local
- [x] Testes do motor estatístico

## Backlog
- IA
- Novas estratégias
- Multi-corretoras
- Auto learning
- Automações (fora da v1.0)
