/**
 * Select tim user (filterOption=false, options lay tu server theo tu khoa): go tu khoa moi
 * thi user DA CHON co the khong con trong ket qua -> AntD hien uid tho. Giu lai option
 * cua user da chon (dat len dau) de nhan luon dung.
 */
export function withSelectedUser(
  options: { value: string; label: string }[],
  selectedValue: string | undefined,
  selectedLabel: string | undefined,
): { value: string; label: string }[] {
  if (!selectedValue || !selectedLabel || options.some((o) => o.value === selectedValue)) {
    return options;
  }
  return [{ value: selectedValue, label: selectedLabel }, ...options];
}
