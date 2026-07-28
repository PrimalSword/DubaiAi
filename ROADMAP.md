# Orbis Trade AI

## Roadmap v1.0

Este documento governa o desenvolvimento do projeto.

### Regra Máxima
Nenhuma funcionalidade poderá ser desenvolvida fora do Sprint atual. Ideias novas entram apenas no Backlog.

## Sprint 1 - Infraestrutura ✅ IMPLEMENTADO — AGUARDA VALIDAÇÃO EM DISPOSITIVO
- [x] Estrutura Android
- [x] Kotlin
- [x] Jetpack Compose
- [x] Clean Architecture
- [x] MVVM
- [x] Overlay
- [x] MediaProjection
- [x] Navegação

Critérios técnicos cumpridos: build validado pelo GitHub Actions; overlay e captura de tela implementados com diagnóstico por contador de frames.

## Sprint 2 - Visão Computacional 🧪 IMPLEMENTADO — AGUARDA CALIBRAÇÃO E VALIDAÇÃO
- [x] OpenCV
- [x] Detecção experimental do gráfico
- [x] Reconstrução inicial de candidatos a candles
- [x] OCR dos textos visíveis, incluindo ativo e tempo quando presentes na tela

Critério pendente de validação: precisão mínima de 95% em capturas reais da corretora. O Sprint somente será considerado validado após os testes em dispositivo e a calibração necessária.

Commit de implementação: `0e2d05efe9074fa7cf5136c2c57a6e0a72c378c8`.

## Sprint 3 - Indicadores
- [ ] EMA
- [ ] Bollinger
- [ ] ATR
- [ ] Lateralidade
- [ ] Tendência
- [ ] Volatilidade

## Sprint 4 - Estratégia Dubai V1
- [ ] Motor de regras
- [ ] Score
- [ ] Alertas
- [ ] Histórico
- [ ] Banco local

## Sprint 5 - Estatísticas
- [ ] Dashboard
- [ ] Win/Loss
- [ ] Heatmap
- [ ] Horários
- [ ] Exportação

## Backlog
- IA
- Novas estratégias
- Multi-corretoras
- Auto learning
- Automações (fora da v1.0)