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

## Sprint 2.1 - Processamento em segundo plano ✅ VALIDADO EM DISPOSITIVO
- [x] Análise executada no foreground service enquanto outro aplicativo está em primeiro plano
- [x] Estado compartilhado independente da Activity
- [x] Overlay atualizado em tempo real com gráfico, candles, tendência e volatilidade
- [x] Diagnóstico persistente ao retornar ao Orbis

Validação realizada com gráfico real da corretora em primeiro plano.

## Sprint 3 - Indicadores ✅ VALIDADO FUNCIONALMENTE — CALIBRAÇÃO CONTÍNUA
- [x] EMA 12
- [x] EMA 60
- [x] Bollinger 12 / 1,5
- [x] ATR 14
- [x] Lateralidade
- [x] Tendência
- [x] Volatilidade
- [x] Testes unitários do motor matemático

Os indicadores utilizam candles reconstruídos em coordenadas normalizadas da imagem. A matemática está coberta por testes; a calibração visual permanece contínua.

## Sprint 4 - Estratégia Dubai V1 🧪 IMPLEMENTADO — AGUARDA VALIDAÇÃO EM DISPOSITIVO
- [x] Motor de regras
- [x] Score de 0 a 100
- [x] Alertas visuais no overlay
- [x] Histórico de sinais
- [x] Banco local SQLite
- [x] Bloqueio de mercado lateral
- [x] Cooldown contra alertas duplicados
- [x] Testes unitários de CALL, PUT e WAIT

Regras principais: EMA 60 confirma a tendência; rompimento da Bollinger 12 / 1,5 confirma a direção; mercado lateral bloqueia entrada; ATR e separação das EMAs ajustam o score. Sinais são exclusivamente experimentais e destinados a conta demo.

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
