# Datos técnicos

Los números exactos, para quien los quiera. Son los valores por defecto; se pueden cambiar
en el archivo config/every_tunnel/gameplay.json.

## Stamina

- 100 puntos.
- Esprintar gasta 12 por segundo; cada salto gasta 8.
- Se recupera a 15 por segundo, 1,5 segundos después de usarla por última vez.
- A 0 te quedas sin aliento hasta que vuelve a 30.

## Sed

- 100 puntos. Baja 5 por minuto: llena dura unos 20 minutos.
- El doble de rápido al esprintar.
- Vacía: 1 de daño (medio corazón) cada 10 segundos.
- Una botella de agua devuelve 40 y se tarda 1,5 segundos en beberla.

## Linterna

- Una batería llena dura 3 minutos de luz.
- Por debajo del 20% la fila de batería parpadea y la luz empieza a fallar.
- Una batería nueva la llena entera y se tarda 1,5 segundos en ponerla.
- El haz llega a 20 bloques.

## Ruido

Hasta dónde llega cada ruido, en bloques, sin nada en medio:

- Agacharse: 2 por paso.
- Clic de la linterna, cambio de batería, beber: 4.
- Jadear: 5, una vez por segundo.
- Andar: 6 por paso.
- Saltar: 8.
- Puertas, botones, palancas, cofres: 8.
- Aterrizar: 10 tras una caída de 2 bloques, desde 5 en caídas pequeñas hasta 20 en caídas
  largas.
- Esprintar: 14 por paso.

Cada bloque sólido en medio recorta la distancia: un muro al 60%, dos muros al 36%.

## El que escucha

- Un ruido lo bastante fuerte hace que corra al momento: por ejemplo, esprintar a unos 7
  bloques sin ningún muro en medio.
- Tras un ruido débil se queda quieto escuchando 2 segundos.
- Tras perderte, busca durante 4 segundos.
- Deja de andar hacia un ruido a los 20 segundos.
- Con un toque basta.

## Tus señales de aviso

- Latido: desde 24 bloques, de 60 a 140 latidos por minuto según se acerca.
- Sus pasos corriendo se oyen hasta a 24 bloques, andando hasta a 16.
