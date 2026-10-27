<template>
  <div class="board-wrap">
    <div class="board" :style="boardStyle" role="grid" aria-label="五子棋棋盘">
      <button
        v-for="(cell, index) in flatBoard"
        :key="index"
        class="cell"
        :class="cellClass(index, cell)"
        :aria-label="`落子位置 ${toXY(index).x + 1},${toXY(index).y + 1}`"
        @click="handleClick(index)"
      >
        <span v-if="isStar(index) && cell === 0" class="star" aria-hidden="true" />
        <span v-if="cell !== 0" class="stone" :class="cell === 1 ? 'black' : 'white'" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  board: number[][];
  disabled?: boolean;
}>();

const emit = defineEmits<{
  place: [x: number, y: number];
}>();

const size = computed(() => props.board.length);
const flatBoard = computed(() => props.board.flat());

const toXY = (index: number) => ({
  x: index % size.value,
  y: Math.floor(index / size.value),
});

const isStarPoint = (x: number, y: number): boolean => {
  if (size.value !== 15) {
    return false;
  }
  const points = [3, 7, 11];
  return points.includes(x) && points.includes(y);
};

const isStar = (index: number): boolean => {
  const { x, y } = toXY(index);
  return isStarPoint(x, y);
};

const cellClass = (index: number, cell: number) => {
  const { x, y } = toXY(index);
  return {
    disabled: props.disabled || cell !== 0,
    occupied: cell !== 0,
    "first-row": y === 0,
    "last-row": y === size.value - 1,
    "first-col": x === 0,
    "last-col": x === size.value - 1,
    "star-point": isStarPoint(x, y),
  };
};

const boardStyle = computed(() => ({
  gridTemplateColumns: `repeat(${size.value}, 1fr)`,
  gridTemplateRows: `repeat(${size.value}, 1fr)`,
}));

const handleClick = (index: number): void => {
  if (props.disabled) {
    return;
  }
  const { x, y } = toXY(index);
  emit("place", x, y);
};
</script>

<style scoped>
.board-wrap {
  width: min(92vw, 560px);
  max-width: 100%;
  aspect-ratio: 1;
  margin: 0 auto;
}

.board {
  display: grid;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #e3bb7a, #d7a45e);
  border: 2px solid #8e5d27;
  border-radius: 10px;
  box-shadow: 0 8px 20px rgb(0 0 0 / 18%);
  padding: clamp(10px, 2.2vw, 14px);
  gap: 0;
  touch-action: manipulation;
}

.cell {
  position: relative;
  border: none;
  background: transparent;
  padding: 0;
  margin: 0;
}

.cell::before,
.cell::after {
  content: "";
  position: absolute;
  background: #6f4a21;
  z-index: 1;
}

/* 横线 */
.cell::before {
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  transform: translateY(-50%);
}

/* 竖线 */
.cell::after {
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
}

/* 外围线段只保留半段，让交叉点更像真实棋盘 */
.cell.first-col::before {
  left: 50%;
}

.cell.last-col::before {
  right: 50%;
}

.cell.first-row::after {
  top: 50%;
}

.cell.last-row::after {
  bottom: 50%;
}

.cell:not(.disabled) {
  cursor: pointer;
}

.cell.disabled {
  cursor: not-allowed;
}

.star {
  position: absolute;
  left: 50%;
  top: 50%;
  width: clamp(4px, 16%, 7px);
  height: clamp(4px, 16%, 7px);
  transform: translate(-50%, -50%);
  background: #5b3a16;
  border-radius: 50%;
  z-index: 2;
}

.stone {
  position: absolute;
  left: 50%;
  top: 50%;
  width: clamp(14px, 78%, 34px);
  height: clamp(14px, 78%, 34px);
  transform: translate(-50%, -50%);
  border-radius: 50%;
  box-shadow: inset 0 -2px 5px rgb(0 0 0 / 40%), 0 2px 4px rgb(0 0 0 / 26%);
  z-index: 3;
}

.stone.black {
  background: radial-gradient(circle at 30% 28%, #666, #111 65%);
}

.stone.white {
  background: radial-gradient(circle at 30% 28%, #fff, #dfdfdf 65%);
  border: 1px solid #bbb;
}

@media (max-width: 430px) {
  .board-wrap {
    width: min(76vw, 340px);
  }
}
</style>
