# Arquitetura — Sprint 1

## Objetivo

Fornecer a infraestrutura Android necessária para que os próximos Sprints processem frames da tela sem acoplamento à interface da corretora.

## Camadas atuais

- `MainActivity` e Compose: apresentação, navegação e solicitação explícita de permissões.
- `MainViewModel`: expõe o estado operacional à interface.
- `OverlayService`: mantém um widget flutuante sobre outros aplicativos.
- `ScreenCaptureService`: mantém a sessão MediaProjection e entrega frames brutos.
- `AppRuntimeState`: contrato de estado observável entre serviços e UI.

## Fluxo

1. O usuário concede a permissão de sobreposição.
2. O app inicia `OverlayService` como foreground service.
3. O usuário autoriza a MediaProjection pelo diálogo oficial do Android.
4. O app inicia `ScreenCaptureService` como foreground service.
5. `ImageReader` recebe frames RGBA da tela.
6. A UI observa o estado e o contador de frames por `StateFlow`.

## Decisões

- Kotlin e Compose para reduzir código de UI e manter estado reativo.
- MVVM para separar apresentação do estado operacional.
- Foreground services para respeitar as restrições modernas do Android.
- Nenhum OpenCV, OCR, indicador ou motor de estratégia foi incluído, pois pertencem aos Sprints seguintes.

## Critério operacional

A infraestrutura é considerada funcional quando o overlay permanece visível sobre outro aplicativo e o contador de frames aumenta durante uma sessão MediaProjection autorizada.
