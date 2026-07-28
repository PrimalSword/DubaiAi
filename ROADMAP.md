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

Validação realizada em dispositivo Android: overlay e MediaProjection permaneceram ativos e a captura contabilizou frames.

## Sprint 2 - Visão Computacional ✅ IMPLEMENTADO
- [x] OpenCV
- [x] Detecção experimental do gráfico
- [x] Reconstrução inicial de candidatos a candles
- [x] OCR dos textos visíveis

## Sprint 2.1 - Processamento em segundo plano 🧪 IMPLEMENTADO — AGUARDA VALIDAÇÃO
- [x] Análise executada no foreground service enquanto outro aplicativo está em primeiro plano
- [x] Estado compartilhado independente da Activity
- [x] Overlay atualizado em tempo real com gráfico, candles, tendência e volatilidade
- [x] Diagnóstico persistente ao retornar ao Orbis

Critério de validação: abrir um gráfico real após iniciar a captura e confirmar resultados no overlay e na tela de diagnóstico.

## Sprint 3 - Indicadores 🧪 IMPLEMENTADO — AGUARDA CALIBRAÇÃO VISUAL
- [x] EMA 12
- [x] EMA 60
- [x] Bollinger 12 / 1,5
- [x] ATR 14
- [x] Lateralidade
- [x] Tendência
- [x] Volatilidade
- [x] Testes unitários do motor matemático

Os indicadores utilizam candles reconstruídos em coordenadas normalizadas da imagem. A matemática está coberta por testes; a precisão depende da calibração da detecção visual em gráficos reais.

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
